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
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.EventRestNotify;
import org.snova.c4.server.service.EventService;
import org.snova.c4.server.service.TimeoutService;
import org.snova.c4.server.servlet.ServletHelper;

/**
 * @author wqy
 * 
 */
public class HTTPPullInvokeServlet extends HttpServlet
{
	private static byte[] CRLF = "\r\n".getBytes();
	private static byte[] LAST_CHUNK = "0\r\n\r\n".getBytes();
			
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
	        throws ServletException, IOException
	{
		String userToken = req.getHeader(C4Constants.USER_TOKEN_HEADER);
		if(null == userToken)
		{
			userToken = "";
		}
		int transacTimeValue = ServletHelper.getTransacTime(req);

		resp.setHeader("Transfer-Encoding", "chunked");
		EventService service = EventService.getInstance(userToken);
		long start = System.currentTimeMillis();
		Buffer buf = new Buffer(4096);
		boolean writeEvents = false;
		while (true)
		{
			TimeoutService.touch(userToken);
			long now = System.currentTimeMillis();
			if (now - start >= transacTimeValue)
			{
				break;
			}
			if (service.getRestEventQueueSize() > 0)
			{
				buf.clear();
				BufferHelper.writeFixInt32(buf, 1, true);
				service.extractEventResponses(buf);
				int k = buf.getWriteIndex();
				int len = buf.readableBytes();
				buf.setWriteIndex(0);
				BufferHelper.writeFixInt32(buf, len - 4, true);
				buf.setWriteIndex(k);
				//resp.getOutputStream().write(Integer.toHexString(buf.readableBytes()).getBytes());
				//resp.getOutputStream().write(CRLF);
				resp.getOutputStream().write(buf.getRawBuffer(), 0,
				        buf.readableBytes());
				//resp.getOutputStream().write(CRLF);
				resp.getOutputStream().flush();	
				writeEvents = true;
			}
			else
			{
				try
				{
					synchronized (service)
                    {
						service.wait(1000);
                    }
					if(!writeEvents)
					{
						service.offer(new EventRestNotify(), null);
					}
				}
				catch (InterruptedException e)
				{
					
				}
			}
		}
		//resp.getOutputStream().write(LAST_CHUNK);
		resp.getOutputStream().flush();
		//resp.getOutputStream().close();
		// resp.getOutputStream().wr
	}
}
