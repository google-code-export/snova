/**
 * 
 */
package org.snova.framework.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpResponse;

/**
 * @author qiyingwang
 * 
 */
public interface LocalProxyHandler
{
	public void handleResponse(RemoteProxyHandler remote, HttpResponse res);

	public void handleChunk(RemoteProxyHandler remote, HttpChunk chunk);

	public void handleRawData(RemoteProxyHandler remote, ByteBuf raw);

	public void close();

	public int getId();

	public Channel getLocalChannel();
}
