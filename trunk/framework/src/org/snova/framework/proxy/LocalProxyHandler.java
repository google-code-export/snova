/**
 * 
 */
package org.snova.framework.proxy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * @author qiyingwang
 * 
 */
public interface LocalProxyHandler
{
	public void handleResponse(RemoteProxyHandler remote, HttpResponse res);

	public void handleChunk(RemoteProxyHandler remote, HttpChunk chunk);

	public void handleRawData(RemoteProxyHandler remote, ChannelBuffer raw);

	public void close();

	public int getId();

	public Channel getLocalChannel();
}
