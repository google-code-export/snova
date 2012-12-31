/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.server.session.RemoteProxySessionV2;

/**
 * @author wqy
 * 
 */
public class PullServlet extends HttpServlet
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	private void flushContent(HttpServletResponse resp, Buffer buf)
	        throws Exception
	{
		resp.setStatus(200);
		resp.setContentType("image/jpeg");
		resp.setHeader("C4LenHeader", "1");
		Buffer len = new Buffer(4);
		BufferHelper.writeFixInt32(len, buf.readableBytes(), true);
		resp.getOutputStream().write(len.getRawBuffer(), len.getReadIndex(),
		        len.readableBytes());
		resp.getOutputStream().write(buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
		resp.getOutputStream().flush();
	}

	private void writeCachedEvents(RemoteProxySessionV2 session,
	        HttpServletResponse resp, Buffer buf, LinkedList<Event> evs, int maxRead) throws Exception
	{
		if (null != session)
		{
			session.extractEventResponses(buf, maxRead, evs);
			if (buf.readableBytes() > 0)
			{
				flushContent(resp, buf);
				buf.clear();
			}
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		long begin = System.currentTimeMillis();
		Buffer buf = new Buffer(4096);
		RemoteProxySessionV2.init();
		String userToken = req.getHeader(C4Constants.USER_TOKEN_HEADER);
		String miscInfo = req.getHeader("C4MiscInfo");
		if (null == userToken)
		{
			userToken = "";
		}
		String[] misc = miscInfo.split("_");
		int timeout = Integer.parseInt(misc[0]);
		int maxRead = Integer.parseInt(misc[1]);

		long deadline = begin + timeout * 1000;
		boolean sentData = false;
		RemoteProxySessionV2 currentSession = null;
		try
		{
			int bodylen = req.getContentLength();
			if (bodylen > 0)
			{
				Buffer content = new Buffer(bodylen);
				int len = 0;
				while (len < bodylen)
				{
					content.read(req.getInputStream());
					len = content.readableBytes();
				}
				if (len > 0)
				{
					currentSession = RemoteProxySessionV2.dispatchEvent(
					        userToken, content);
				}
			}

			LinkedList<Event> evs = new LinkedList<Event>();
			do
			{
				evs.clear();
				if (System.currentTimeMillis() >= deadline)
				{
					break;
				}
				writeCachedEvents(currentSession, resp, buf, evs, maxRead);
				if (!currentSession.isReady())
				{
					Thread.sleep(1);
					continue;
				}
				writeCachedEvents(currentSession, resp, buf, evs, maxRead);
				if (null != currentSession && currentSession.isClosing())
				{
					break;
				}
				int timeoutsec = (int) ((deadline - System.currentTimeMillis()) / 1000);
				if (timeoutsec == 0)
				{
					break;
				}
				if (null != currentSession && currentSession.isReady())
				{
					currentSession.readClient(maxRead, timeoutsec);
				}
				if (System.currentTimeMillis() >= deadline)
				{
					break;
				}

			}
			while (true);

			int size = buf.readableBytes();
			try
			{
				sentData = true;
				resp.getOutputStream().close();
			}
			catch (Exception e)
			{
				logger.error("Requeue events since write " + size
				        + " bytes while exception occured.", e);
				e.printStackTrace();
				if (null != currentSession)
				{
					currentSession.requeueEvents(evs);
				}
				buf.clear();
				resp.getOutputStream().close();
			}
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
		}
		if (!sentData)
		{
			resp.setStatus(200);
			resp.setContentLength(0);
		}
	}
}