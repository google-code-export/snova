/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.arch.event.Event;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpWriteHandlerCallback extends
        FutureCallback.FutureCallbackAdapter
{
	Event[]	             cacheEvent;
	LinkedList<Event>	 appendingEvents	= new LinkedList<Event>();
	private HttpDualConn	conn;
	private int	         waitTime	     = 1;
	
	public HttpWriteHandlerCallback(HttpDualConn conn)
	{
		this.conn = conn;
	}
	
	void offerEvent(Event ev)
	{
		appendingEvents.add(ev);
	}
	
	Event[] getAllCacheEvent()
	{
		if (null != cacheEvent)
		{
			for (int i = cacheEvent.length - 1; i >= 0; i--)
			{
				appendingEvents.addFirst(cacheEvent[i]);
			}
		}
		Event[] es = new Event[appendingEvents.size()];
		return appendingEvents.toArray(es);
	}
	
	@Override
	public void onResponse(HttpResponse res)
	{
		if (res.getStatus().getCode() >= 500)
		{
			System.out.println(res.getContent().toString(
			        Charset.forName("utf8")));
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			{
				@Override
				public void run()
				{
					conn.startWriteTask(getAllCacheEvent());
				}
			}, waitTime, TimeUnit.SECONDS);
			waitTime *= 2;
		}
		else
		{
			cacheEvent = null;
			conn.write = null;
			if (!appendingEvents.isEmpty())
			{
				Event[] es = new Event[appendingEvents.size()];
				appendingEvents.toArray(es);
				conn.startWriteTask(es);
			}
		}
	}
	
	@Override
	public void onError(String error)
	{
		System.out.println("###############" + error);
		SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
		{
			public void run()
			{
				conn.startWriteTask(getAllCacheEvent());
			}
		}, waitTime, TimeUnit.SECONDS);
		waitTime *= 2;
	}
}
