/**
 * 
 */
package org.snova.c4.server.service;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.arch.buffer.Buffer;
import org.arch.util.ListSelector;
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
	private static Map<String, ListSelector<Channel>> remoteChannelTable = new ConcurrentHashMap<String, ListSelector<Channel>>();
	
	
	public static void closeConnections(String token)
	{
		remoteChannelTable.remove(token);
	}
	
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
		//System.out.println("########Connect " + addr);
		ChannelFuture future = channel.connect(addr);
		return future;
	}

	public static void routine(String auth, String remote, final String token)
	{
		TimeoutService.touch(token);
		String[] ss = remote.split("_");
		String host = ss[0];
		int port = Integer.parseInt(ss[1]);
		int connSize = Integer.parseInt(ss[2]);
		final InetSocketAddress address = new InetSocketAddress(host, port);
		ListSelector<Channel> selector = remoteChannelTable.get(token);
		if (null == selector)
		{
			selector = new ListSelector<Channel>();
			remoteChannelTable.put(token, selector);
		}
		final ListSelector<Channel> tmp = selector;
		for (int i = selector.size(); i < connSize; i++)
		{
			final RSocketAcceptedEvent ev = new RSocketAcceptedEvent();
			String[] sss = auth.split(":");
			ev.domain = sss[0];
			ev.port = 80;
			if(sss.length > 1)
			{
				ev.port = Integer.parseInt(sss[1]);
			}
			
			ChannelFuture future = newRemoteChannelFuture(address, token);
			future.addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture f) throws Exception
				{
					if (f.isSuccess())
					{
						tmp.add(f.getChannel());
						Buffer content = new Buffer(16);
						ev.encode(content);
						ChannelBuffer msg = ChannelBuffers.wrappedBuffer(
						        content.getRawBuffer(), content.getReadIndex(),
						        content.readableBytes());
						f.getChannel().write(msg);
					}
					else
					{
						closeRsocketChannel(token, f.getChannel());
					}
				}
			});
		}
	}

	private static void closeRsocketChannel(String token, Channel ch)
	{
		ListSelector<Channel> selector = remoteChannelTable.get(token);
		if (null != ch && ch.isConnected())
		{
			ch.close();	
		}
		if (null != selector)
		{
			selector.remove(ch);
		}
	}

	public static void eventNotify(String token)
	{
		ListSelector<Channel> selector = remoteChannelTable.get(token);
		EventService serv = EventService.getInstance(token);
		Channel ch = selector.select();
		if(null == ch)
		{
			return;
		}
		while(serv.getRestEventQueueSize() > 0)
		{
			Buffer[] tmp = new Buffer[selector.size()];
			serv.extractEventResponses(tmp, 512*1024);
			for(int i = 0; i < tmp.length; i++)
			{
				ch = selector.get(i);
				if(ch == null)
				{
					ch = selector.select();
				}
				
				if(null != tmp[i] && tmp[i].readable())
				{
					ChannelBuffer message = ChannelBuffers
					        .wrappedBuffer(tmp[i].getRawBuffer(), tmp[i].getReadIndex(),
					                tmp[i].readableBytes());
					ch.write(message);
				}
			}

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
			System.out.println("########closed connection for  " + userToken);
			closeRsocketChannel(userToken, ctx.getChannel());
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			e.getCause().printStackTrace();
			// super.exceptionCaught(ctx, e);
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			TimeoutService.touch(userToken);
			Object msg = e.getMessage();
			if (msg instanceof ChannelBuffer)
			{
				ChannelBuffer buf = (ChannelBuffer) msg;
				if(buf.readable())
				{
					byte[] raw = new byte[buf.readableBytes()];
					buf.readBytes(raw);
					Buffer content = Buffer.wrapReadableContent(raw);
					EventService.getInstance(userToken).dispatchEvent(content);
				}
			}
			else
			{
				// logger.error("Unsupported message type:" +
				// msg.getClass().getName());
			}
		}
	}
}
