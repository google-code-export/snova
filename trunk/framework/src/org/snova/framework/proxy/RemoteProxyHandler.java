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
	public void handleRequest(LocalProxyHandler local, HttpRequest req);

	public void handleChunk(LocalProxyHandler local, HttpChunk chunk);

	public void handleRawData(LocalProxyHandler local, ByteBuf raw);

	public void close();
}
