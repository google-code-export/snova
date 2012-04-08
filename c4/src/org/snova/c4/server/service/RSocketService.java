/**
 * 
 */
package org.snova.c4.server.service;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.arch.buffer.Buffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.snova.c4.common.event.RSocketAcceptedEvent;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public class RSocketService
{
	private static ClientSocketChannelFactory factory;
	private static Map<String, Channel> remoteChannelTable = new ConcurrentHashMap<String, Channel>();

	protected static ClientSocketChannelFactory getClientSocketChannelFactory()
	{
		if (null == factory)
		{
			ExecutorService workerExecutor = SharedObjectHelper
			        .getGlobalThreadPool();
			if (null == workerExecutor)
			{
				workerExecutor = Executors.newFixedThreadPool(25);
				SharedObjectHelper.setGlobalThreadPool(workerExecutor);
			}
			factory = new NioClientSocketChannelFactory(workerExecutor,
			        workerExecutor);
		}
		return factory;
	}

	protected static ChannelFuture newRemoteChannelFuture(
	        InetSocketAddress addr, String token)
	{
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new LengthFieldBasedFrameDecoder(
		        1024 * 1024 * 10, 0, 4, 0, 4));
		pipeline.addLast("encoder", new LengthFieldPrepender(4));
		pipeline.addLast("handler", new RSocketMessageHandler(addr, token));

		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		channel.getConfig().setOption("connectTimeoutMillis", 40 * 1000);
		System.out.println("########Connect " + addr);
		ChannelFuture future = channel.connect(addr);
		return future;
	}

	public static void routine(String auth, String remote, final String token)
	{
		TimeoutService.touch(token);
		String[] ss = remote.split(":");
		String host = ss[0];
		int port = Integer.parseInt(ss[1]);
		final InetSocketAddress address = new InetSocketAddress(host, port);
		//System.out.println("########" + token);
		Channel ch = remoteChannelTable.get(token);
//		if(null !=ch && !ch.getRemoteAddress().equals(address))
//		{
//			closeRsocketChannel(token);
//     		ch = null;
//		}
		if (null == ch)
		{
			final RSocketAcceptedEvent ev = new RSocketAcceptedEvent();
			String[] sss = auth.split(":");
			ev.domain = sss[0];
			if (sss.length > 1)
			{
				ev.port = Integer.parseInt(sss[1]);
			}

			ChannelFuture future = newRemoteChannelFuture(address, token);
			remoteChannelTable.put(token, future.getChannel());
			future.addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture f) throws Exception
				{
					if (f.isSuccess())
					{
						Buffer content = new Buffer(16);
						ev.encode(content);
						ChannelBuffer msg = ChannelBuffers.wrappedBuffer(
						        content.getRawBuffer(), content.getReadIndex(),
						        content.readableBytes());
						f.getChannel().write(msg);
					}
					else
					{
						closeRsocketChannel(token);
					}
				}
			});
		}
	}

	private static void closeRsocketChannel(String token)
	{
		
		Channel channel = remoteChannelTable.remove(token);
		
		if (null != channel && channel.isConnected())
		{
			channel.close();
		}
	}

	public static void eventNotify(String token)
	{
		Channel channel = remoteChannelTable.get(token);
		if (null != channel)
		{
			Buffer tmp = new Buffer(1024);
			EventService.getInstance(token).extractEventResponses(tmp, 8192);
			ChannelBuffer message = ChannelBuffers
			        .wrappedBuffer(tmp.getRawBuffer(), tmp.getReadIndex(),
			                tmp.readableBytes());
			channel.write(message);
		}
	}

	static class RSocketMessageHandler extends SimpleChannelUpstreamHandler
	{
		private InetSocketAddress addr;
		private String userToken;

		public RSocketMessageHandler(InetSocketAddress addr, String userToken)
		{
			this.addr = addr;
			this.userToken = userToken;
		}
		
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			closeRsocketChannel(userToken);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			// super.exceptionCaught(ctx, e);
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object msg = e.getMessage();
			if (msg instanceof ChannelBuffer)
			{
				ChannelBuffer buf = (ChannelBuffer) msg;
				byte[] raw = new byte[buf.readableBytes()];
				buf.readBytes(raw);
				Buffer content = Buffer.wrapReadableContent(raw);
				EventService.getInstance(userToken).dispatchEvent(content);
			}
			else
			{
				// logger.error("Unsupported message type:" +
				// msg.getClass().getName());
			}
		}
	}
}
