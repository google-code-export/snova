/**
 * 
 */
package org.snova.framework.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author yinqiwen
 * 
 */
public class ProxyServer
{
	protected static Logger logger = LoggerFactory.getLogger(ProxyServer.class);

	private ServerBootstrap bootstrap = new ServerBootstrap();
	private ChannelFuture server = null;

	public ProxyServer(SimpleSocketAddress listenAddress)
	{
		String host = listenAddress.host;
		int port = listenAddress.port;
		try
		{
			bootstrap
			        .group(SharedObjectHelper.getEventLoop(),
			                SharedObjectHelper.getEventLoop())
			        .channel(NioServerSocketChannel.class)
			        .localAddress(new InetSocketAddress(host, port))
			        .childHandler(new ChannelInitializer<Channel>()
			        {
				        @Override
				        public void initChannel(Channel ch) throws Exception
				        {
					        ChannelPipeline p = ch.pipeline();
					        p.addLast("decoder", new HttpRequestDecoder());
					        p.addLast("encoder", new HttpResponseEncoder());
					        p.addLast("handler", new ProxyHandler());
				        }
			        });

			server = bootstrap.bind().sync();
			if (!server.isSuccess())
			{
				logger.error("Failed to start proxy server");
			}
		}
		catch (Exception e)
		{
			logger.error("Failed to start proxy server.", e);
		}
	}

	public void close()
	{
		if (null != server)
		{
			server.channel().close();
			server = null;
		}
	}
}
