/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.EventRestNotify;
import org.snova.c4.server.service.EventService;
import org.snova.c4.server.service.TimeoutService;
import org.snova.c4.server.session.DirectSession;
import org.snova.c4.server.session.SessionManager;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 * 
 */
public class HTTPEventDispatcherServlet extends HttpServlet
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	
	public HTTPEventDispatcherServlet()
	{
	}
	
	private void writeBytes(HttpServletResponse resp, byte[] buf, int off,
	        int len) throws IOException
	{
		int maxWriteLen = 8192;
		int writed = 0;
		while (writed < len)
		{
			int writeLen = maxWriteLen;
			if (writed + writeLen > len)
			{
				writeLen = len - writed;
			}
			resp.getOutputStream().write(buf, off + writed, writeLen);
			resp.getOutputStream().flush();
			writed += writeLen;
		}
		//resp.getOutputStream().close();
	}
	
	private void send(HttpServletResponse resp, Buffer buf) throws Exception
	{
		// resp.setBufferSize(buf.readableBytes() + 100);
		resp.setStatus(200);
		resp.setContentType("image/jpeg");
		// resp.setHeader("Cache-Control", "no-cache");
		resp.setContentLength(buf.readableBytes());
		// resp.getOutputStream().write(buf.getRawBuffer(), buf.getReadIndex(),
		// buf.readableBytes());
		// resp.getOutputStream().flush();
		writeBytes(resp, buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
		// resp.getOutputStream().close();
	}
	
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		String userToken = req.getHeader(C4Constants.USER_TOKEN_HEADER);
		if (null == userToken)
		{
			userToken = "";
		}
		String actor = req.getHeader("ClientActor");
		boolean assist = false;
		if (actor != null && actor.equals("Assist"))
		{
			assist = true;
		}
		int transacTimeValue = ServletHelper.getTransacTime(req);
		//long start = System.currentTimeMillis();
		boolean sentData = false;
		TimeoutService.touch(userToken);
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
					EventService service = EventService.getInstance(userToken);
					service.dispatchEvent(content);
					if (assist)
					{
						synchronized (service)
						{
							if (service.getRestEventQueueSize() == 0)
							{
								service.wait(transacTimeValue - 5000);
							}
						}
					}
					Buffer buf = new Buffer(4096);
					int maxResSize = ServletHelper.getMaxResponseSize(req);
					service.extractEventResponses(buf, maxResSize);
//					while (assist && buf.readableBytes() < maxResSize)
//					{
//						long now = System.currentTimeMillis();
//						if (now - start < 2000)
//						{
//							synchronized (service)
//							{
//								service.wait(2000);
//							}
//						}
//						else
//						{
//							break;
//						}
//						service.extractEventResponses(buf, maxResSize);
//					}
					int size = buf.readableBytes();
					try
					{
						sentData = true;
						send(resp, buf);
					}
					catch (Exception e)
					{
						logger.error("Requeue events since write " + size
						        + " bytes while exception occured.", e);
					}
				}
			}
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
			// logger.warn("Failed to process message", e);
		}
		TimeoutService.touch(userToken);
		if (!sentData)
		{
			resp.setStatus(200);
			resp.setContentLength(0);
		}
		// resp.getOutputStream().close();
	}
}
