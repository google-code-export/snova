/**
 * 
 */
package org.snova.c4.server.io;

import java.io.IOException;
import java.util.LinkedList;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.snova.c4.server.session.v3.RemoteProxySessionManager;

/**
 * @author yinqiwen
 * 
 */
public class AsyncPuller extends Puller implements AsyncListener
{
	private AsyncContext ctx;
	private boolean working = false;;
	private boolean closing = false;

	public AsyncPuller(HttpServletRequest req, long timeout)
	{
		ctx = req.startAsync();
		ctx.setTimeout(timeout);
		ctx.addListener(this);
	}

	public void doWork(LinkedList<Event> evs) throws IOException
	{
		working = true;
		if (null != evs)
		{
			Buffer buf = new Buffer(4096);
			while (!evs.isEmpty())
			{
				if (closing)
				{
					return;
				}
				Event ev = evs.removeFirst();
				if (RemoteProxySessionManager.getInstance().sessionExist(
				        userToken, index, ev.getHash()))
				{
					ev.encode(buf);
					try
					{
						flushContent(ctx.getResponse(), buf);
					}
					catch (Exception e)
					{
						evs.addFirst(ev);
						e.printStackTrace();
						ctx.complete();
						break;
					}
					buf.clear();
				}
			}
		}
		working = false;
	}

	@Override
	public void onComplete(AsyncEvent ev) throws IOException
	{
		closing = true;
		removePuller(userToken, index);
	}

	@Override
	public void onError(AsyncEvent ev) throws IOException
	{
		closing = true;
		ctx.getResponse().getOutputStream().close();
		removePuller(userToken, index);
	}

	@Override
	public void onStartAsync(AsyncEvent ev) throws IOException
	{

	}

	@Override
	public void onTimeout(AsyncEvent ev) throws IOException
	{
		closing = true;
		if (!working)
		{
			ctx.complete();
			ctx.getResponse().getOutputStream().close();
			removePuller(userToken, index);
		}
	}

}
