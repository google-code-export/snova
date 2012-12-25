/**
 * 
 */
package org.snova.framework.httpserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author yinqiwen
 * 
 */
public class HttpLocalProxyServer
{
	protected static Logger logger = LoggerFactory
	        .getLogger(HttpLocalProxyServer.class);

	private ServerBootstrap bootstrap;

	public HttpLocalProxyServer(SimpleSocketAddress listenAddress)
	{
		String host = listenAddress.host;
		int port = listenAddress.port;
		bootstrap = new ServerBootstrap();
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
					        p.addLast("handler",
					                new HttpLocalProxyRequestHandler());
				        }
			        });

			Channel ch = bootstrap.bind().sync().channel();
			ch.closeFuture().sync();
		}
		catch (Exception e)
		{
			logger.error("Failed to start proxy server.", e);
		}
		finally
		{
			bootstrap.shutdown();
		}
	}
}
