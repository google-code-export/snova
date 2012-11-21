/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.C4PluginVersion;
import org.snova.c4.server.session.RemoteProxySession;
import org.snova.c4.server.session.RemoteProxySessionV2;

/**
 * @author wqy
 * 
 */
public class PushPullServlet extends HttpServlet
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	
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
		// resp.getOutputStream().close();
	}
	
	private void send(HttpServletResponse resp, Buffer buf) throws Exception
	{
		resp.setStatus(200);
		resp.setContentType("image/jpeg");
		resp.setContentLength(buf.readableBytes());
		writeBytes(resp, buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
	}
	
	private void flushContent(HttpServletResponse resp, Buffer buf)
	        throws Exception
	{
		resp.setStatus(200);
		resp.setContentType("image/jpeg");
		Buffer len = new Buffer(4);
		BufferHelper.writeFixInt32(len, buf.readableBytes(), true);
		resp.getOutputStream().write(len.getRawBuffer(), len.getReadIndex(),
		        len.readableBytes());
		resp.getOutputStream().write(buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
		resp.getOutputStream().flush();
	}
	
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		Buffer buf = new Buffer(4096);
		RemoteProxySessionV2.init();
		String userToken = req.getHeader(C4Constants.USER_TOKEN_HEADER);
		String miscInfo = req.getHeader("C4MiscInfo");
		if (null == userToken)
		{
			userToken = "";
		}
		boolean sentData = false;
		HashSet<RemoteProxySessionV2> ss = new HashSet<RemoteProxySessionV2>();
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
					RemoteProxySessionV2.dispatchEvent(userToken, content, ss);
				}
			}
			String[] misc = miscInfo.split("_");
			String role = misc[0];
			int timeout = Integer.parseInt(misc[1]);
			int maxRead = Integer.parseInt(misc[2]);
			boolean isPull = role != null && role.equals("pull");
			System.out.println("Process role:" + role + " session size:"
			        + ss.size());
			long begin = System.currentTimeMillis();
			do
			{
				for (RemoteProxySessionV2 s : ss)
				{
					s.extractEventResponses(buf, maxRead);
				}
				if (isPull)
				{
					if (buf.readableBytes() > 0)
					{
						flushContent(resp, buf);
						buf.clear();
					}
					else
					{
						Thread.sleep(1);
					}
					for (RemoteProxySessionV2 s : ss)
					{
						s.readClient(maxRead, timeout);
					}
					if ((System.currentTimeMillis() - begin) >= timeout * 1000)
					{
						break;
					}
				}
				
			} while (isPull);
			
			int size = buf.readableBytes();
			try
			{
				sentData = true;
				if (!isPull)
				{
					send(resp, buf);
				}
				else
				{
					resp.getOutputStream().close();
				}
			}
			catch (Exception e)
			{
				logger.error("Requeue events since write " + size
				        + " bytes while exception occured.", e);
				buf.setReadIndex(0);
			}
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
			// logger.warn("Failed to process message", e);
		}
		if (!sentData)
		{
			resp.setStatus(200);
			resp.setContentLength(0);
		}
		// resp.getOutputStream().close();
	}
}
