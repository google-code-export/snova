/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.nio.charset.Charset;
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
	Event cacheEvent;
	private HttpDualConn conn;
	private int waitTime = 1;

	public HttpWriteHandlerCallback(HttpDualConn conn)
	{
		this.conn = conn;
	}

	@Override
	public void onResponse(HttpResponse res)
	{
		if (res.getStatus().getCode() != 200)
		{
			System.out.println(res.getContent().toString(
			        Charset.forName("utf8")));
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			{

				@Override
				public void run()
				{
					conn.startWriteTask(cacheEvent);
				}
			}, waitTime, TimeUnit.SECONDS);
			waitTime *= 2;
		}
	}

	@Override
	public void onError(String error)
	{
		SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
		{

			@Override
			public void run()
			{
				conn.startWriteTask(cacheEvent);
			}
		}, waitTime, TimeUnit.SECONDS);
		waitTime *= 2;

	}
}
