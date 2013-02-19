/**
 * 
 */
package org.snova.c4.server.io;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;

import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.snova.c4.server.session.v3.RemoteProxySessionManager;

/**
 * @author yinqiwen
 * 
 */
public class SyncPuller extends Puller
{
	private long begin = System.currentTimeMillis();
	private long timeout;
	private HttpServletResponse resp;

	public SyncPuller(HttpServletResponse resp, long timeout)
	{
		this.resp = resp;
	}

	public void doWork(LinkedList<Event> l) throws IOException
	{
		Buffer buf = new Buffer(4096);
		long deadline = begin + timeout;
		boolean sentData = false;
		try
		{
			do
			{
				RemoteProxySessionManager.getInstance().consumeReadyEvent(
				        userToken, index, buf, timeout);
				if (buf.readable())
				{
					flushContent(resp, buf);
					sentData = true;
					buf.clear();
				}
				timeout = deadline - System.currentTimeMillis();
				if (timeout <= 0)
				{
					break;
				}
			}
			while (true);

			try
			{
				sentData = true;
				resp.getOutputStream().close();
			}
			catch (Exception e)
			{
				logger.error(".", e);
				e.printStackTrace();

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
		finally
		{
			RemoteProxySessionManager.getInstance().pauseSessions(userToken,
			        index);
		}
		if (!sentData)
		{
			resp.setContentLength(0);
		}
		removePuller(userToken, index);
	}
}
