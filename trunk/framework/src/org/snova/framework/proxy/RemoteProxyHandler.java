/**
 * 
 */
package org.snova.framework.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 * 
 */
public interface RemoteProxyHandler
{
	public void handleRequest(HttpRequest req);

	public void handleChunk(HttpChunk chunk);

	public void handleRawData(ByteBuf raw);

	public void close();
}
