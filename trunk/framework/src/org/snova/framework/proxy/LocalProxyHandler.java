/**
 * 
 */
package org.snova.framework.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * @author qiyingwang
 * 
 */
public interface LocalProxyHandler
{
	public void handleResponse(HttpResponse res);

	public void handleChunk(HttpChunk chunk);

	public void handleRawData(ByteBuf raw);

	public void close();
}
