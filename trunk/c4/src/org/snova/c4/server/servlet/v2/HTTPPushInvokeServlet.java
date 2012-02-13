/**
 * 
 */
package org.snova.c4.server.servlet.v2;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.server.service.EventService;
import org.snova.c4.server.service.TimeoutService;

/**
 * @author wqy
 * 
 */
public class HTTPPushInvokeServlet extends HttpServlet
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
	        throws ServletException, IOException
	{
		String userToken = req.getHeader("UserToken");
		if (null == userToken)
		{
			userToken = "";
		}

		Buffer content = new Buffer(4096);
		try
		{
			byte[] buf = new byte[8192];
			do
			{
				TimeoutService.touch(userToken);
				int len = req.getInputStream().read(buf);
				// content = reader.readChunk();
				boolean closed = false;
				if (len > 0)
				{
					content.write(buf, 0, len);
					int size = BufferHelper.readFixInt32(content, true);
					while (size > content.readableBytes())
					{
						TimeoutService.touch(userToken);
						int left = size - content.readableBytes();
						if (left > buf.length)
						{
							left = buf.length;
						}
						len = req.getInputStream().read(buf, 0, left);
						if (len > 0)
						{
							content.write(buf, 0, len);
						}
						else
						{
							closed = true;
							break;
						}
					}
					if (closed)
					{
						break;
					}
					if(size == content.readableBytes())
					{
						EventService.getInstance(userToken).dispatchEvent(content);
						content.clear();
					}
					else
					{
						System.out.println("##############" + content.readableBytes() + "--" + size);
					}
				}
				else
				{
					break;
				}
				
			} while (true);
		}
		catch (Exception e)
		{
			logger.error("", e);
		}
		resp.setContentLength(0);
		resp.setStatus(200);
	}
}
