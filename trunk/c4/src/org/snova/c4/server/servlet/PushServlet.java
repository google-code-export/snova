/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashSet;
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
public class PushServlet extends HttpServlet
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		Buffer buf = new Buffer(4096);
		RemoteProxySessionV2.init();
		String userToken = req.getHeader(C4Constants.USER_TOKEN_HEADER);
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
				try
                {
	                RemoteProxySessionV2.dispatchEvent(userToken,
	                        content);
                }
                catch (Exception e)
                {
	                // TODO Auto-generated catch block
	                e.printStackTrace();
                }
			}
		}
		else
		{
			byte[] buffer = new byte[4096];
			int length = -1;
			while (true)
			{
				int n = req.getInputStream().read(buffer);
				if (n <= 0)
				{
					break;
				}
				buf.write(buffer, 0, n);
				if (length == -1)
				{
					if (buf.readableBytes() < 4)
					{
						continue;
					}
					length = BufferHelper.readFixInt32(buf, true);
				}
				if (length > 0 && buf.readableBytes() >= length)
				{
					Buffer content = new Buffer(length);
					content.write(buf, length);
					buf.discardReadedBytes();
					try
					{
						RemoteProxySessionV2.dispatchEvent(userToken, content);
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					length = -1;
				}
			}
		}
		resp.setContentLength(0);
		resp.setStatus(200);
		// resp.getOutputStream().close();
	}
}
