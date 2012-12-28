/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import io.netty.handler.codec.http.HttpResponse;

import org.arch.event.Event;
import org.snova.http.client.HttpClientCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpWriteHandlerCallback extends
        HttpClientCallback.HttpClientCallbackAdapter
{
	Event cacheEvent;

	@Override
	public void onResponse(HttpResponse res)
	{
		if (res.getStatus().getCode() != 200)
		{

		}
	}

	@Override
	public void onError(String error)
	{

	}
}
