/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.nio.charset.Charset;

import io.netty.handler.codec.http.HttpResponse;

import org.arch.event.Event;
import org.snova.http.client.FutureCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpWriteHandlerCallback extends
        FutureCallback.FutureCallbackAdapter
{
	Event	cacheEvent;
	private HttpDualConn	conn;
	
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
			conn.startWriteTask(cacheEvent);
		}
	}
	
	@Override
	public void onError(String error)
	{
		conn.startWriteTask(cacheEvent);
	}
}
