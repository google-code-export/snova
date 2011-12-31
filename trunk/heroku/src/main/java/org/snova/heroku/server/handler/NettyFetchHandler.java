/**
 * 
 */
package org.snova.heroku.server.handler;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.ListSelector;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.codec.HerokuRawSocketEventFrameDecoder;
import org.snova.heroku.common.event.EventRestNotify;
import org.snova.heroku.common.event.HerokuRawSocketEvent;

/**
 * @author qiyingwang
 * 
 */
public class NettyFetchHandler implements FetchHandler, Runnable
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private NioClientSocketChannelFactory factory;
	private ServerEventHandler handler;
	private long lastTouchTime;
	private Map<Integer, SocketChannel> connectedChannelTable = new ConcurrentHashMap<Integer, SocketChannel>();
	//private SocketChannel localSocketChannel = null;
	private List<SocketChannel> localSocketChannelList = new LinkedList<SocketChannel>();
	private String domain;
	public NettyFetchHandler(ServerEventHandler serverEventHandler)
	{
		this.handler = serverEventHandler;
		factory = new NioClientSocketChannelFactory(
		        Executors.newCachedThreadPool(),
		        Executors.newCachedThreadPool());
		handler.getThreadPool().scheduleAtFixedRate(this, 1, 2, TimeUnit.SECONDS);
	}
	
	private void handleWriteQueue()
	{
		if(localSocketChannelList.size() > 0)
		{
			Buffer buf = new Buffer(4096);
			List<Event> sentEvent = new LinkedList<Event>();
			LinkedList<Event> responseQueue = handler.getEventQueue();
			if(!responseQueue.isEmpty())
			{
				System.out.println("###Write queue size:" + responseQueue.size());
			}
			
			boolean haveData = false;
			do
			{
				if (buf.readableBytes() >= 1024 * 1024)
				{
					break;
				}
				Event ev = null;
				synchronized (responseQueue)
				{
					if (responseQueue.isEmpty())
					{
						break;
					}
					ev = responseQueue.removeFirst();
				}
				ev.encode(buf);
				System.out.println("###Write back event:" + ev.getHash());
				sentEvent.add(ev);
				haveData = true;
			}
			while (true);
			if(haveData)
			{	
				HerokuRawSocketEvent raw = new HerokuRawSocketEvent(domain, buf);
				Buffer content = new Buffer(buf.readableBytes() + 100);
				raw.encode(content);
				ChannelBuffer msg = ChannelBuffers.wrappedBuffer(content.getRawBuffer(), 0, content.readableBytes());
				localSocketChannelList.get(0).write(msg);
			}
		}
	}

	public long touch()
	{
		long now = System.currentTimeMillis();
		lastTouchTime = now;
		return now;
	}
	
	public void handleHerokuAuth(String auth)
	{
		if(localSocketChannelList.size() > 0)
		{
			return;
		}
		String[] ss = auth.split("-");
		this.domain = ss[0];
		String host = ss[1];
		int port = Integer.parseInt(ss[2]);
		for (int i = 0; i <1; i++)
        {
			ChannelPipeline pipeline = Channels.pipeline();
			pipeline.addLast("handler", new LocalSocketResponseHandler());
			final SocketChannel tmp =  factory.newChannel(pipeline);
			localSocketChannelList.add(tmp);
			tmp.connect(new InetSocketAddress(host, port)).addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture future) throws Exception
				{
					handler.offer(new EventRestNotify(), false);
					handleWriteQueue();
				}
			});
        }
		
	}
	
	private void closeAllClient()
	{
		for (SocketChannel channel : connectedChannelTable.values())
		{
			channel.close();
		}
		connectedChannelTable.clear();
		handler.clearEventQueue();
	}
	
	private void closeChannel(int hash)
	{
		SocketChannel channel = connectedChannelTable.remove(hash);
		if(null != channel&&channel.isOpen())
		{
			channel.close();
		}
		HTTPConnectionEvent ev = new HTTPConnectionEvent(
		        HTTPConnectionEvent.CLOSED);
		ev.setHash(hash);
		handler.offer(ev, true);
	}

	private ChannelBuffer buildSentBuffer(HTTPRequestEvent ev)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(ev.method).append(" ").append(ev.url).append(" ")
		        .append("HTTP/1.1\r\n");
		for (KeyValuePair<String, String> header : ev.headers)
		{
			buffer.append(header.getName()).append(": ")
			        .append(header.getValue()).append("\r\n");
		}
		buffer.append("\r\n");

		Buffer msg = new Buffer(buffer.length() + ev.content.readableBytes());
		msg.write(buffer.toString().getBytes());
		if (ev.content.readable())
		{
			msg.write(ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
		}

		return ChannelBuffers.wrappedBuffer(msg.getRawBuffer(), 0,
		        msg.readableBytes());
	}

	public void fetch(HTTPChunkEvent event)
	{
		SocketChannel channel = connectedChannelTable.get(event.getHash());
		if (null != channel)
		{
			channel.write(ChannelBuffers.wrappedBuffer(event.content));
		}
		else
		{
			logger.error("No connected connection found for chunk:"
			        + event.getHash() + " with content size:"
			        + event.content.length);
		}
	}

	public void fetch(final HTTPRequestEvent event)
	{
		// System.out.println("#####Handle HTTPRequestEvent");
		String host = event.getHeader("Host");
		int port = event.url.startsWith("https") ? 443 : 80;
		if (host.indexOf(":") != -1)
		{
			String old = host;
			host = old.substring(0, old.indexOf(":"));
			port = Integer.parseInt(old.substring(old.indexOf(":") + 1));
		}
		if (NetworkHelper.isPrivateIP(host))
		{
			return;
		}
		SocketChannel channel = connectedChannelTable.get(event.getHash());
		if(channel == null || !channel.isConnected())
		{
			ChannelPipeline pipeline = Channels.pipeline();
			pipeline.addLast("handler", new SocketResponseHandler(event.getHash()));
			final SocketChannel tmp =  factory.newChannel(pipeline);
			tmp.connect(new InetSocketAddress(host, port)).addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture future) throws Exception
				{
					if(event.method.equalsIgnoreCase("Connect"))
					{
						HTTPResponseEvent res = new HTTPResponseEvent();
						res.statusCode = 200;
						res.setHash(event.getHash());
						handler.offer(res, false);
					}
					else
					{
						future.getChannel().write(buildSentBuffer(event));
					}
					connectedChannelTable.put(event.getHash(), tmp);
					
				}
			});
		}
		else
		{
			channel.write(buildSentBuffer(event));
		}
	}

	public void handleConnectionEvent(HTTPConnectionEvent ev)
	{
		if (ev.status == HTTPConnectionEvent.CLOSED)
		{
			SocketChannel channel = connectedChannelTable.remove(ev.getHash());
			if (null != channel)
			{
				channel.close();
			}
		}
	}
	
	@ChannelPipelineCoverage("one")
	class LocalSocketResponseHandler extends SimpleChannelUpstreamHandler
	{
		HerokuRawSocketEventFrameDecoder decoder = new HerokuRawSocketEventFrameDecoder();
		public LocalSocketResponseHandler()
        {
        }
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
		    //
		}
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			localSocketChannelList.remove((SocketChannel)ctx.getChannel());
			closeAllClient();
		}
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
		    Object msg = e.getMessage();
		    if(msg instanceof ChannelBuffer)
		    {
		    	ChannelBuffer buf = (ChannelBuffer) msg;
		    	byte[] tmp = new byte[buf.readableBytes()];
		    	buf.readBytes(tmp);
				Buffer content = Buffer.wrapReadableContent(tmp);
				while(content.readable())
				{
					HerokuRawSocketEvent ev = decoder.decode(content);
					if(null == ev)
					{
						System.out.println("####Rest" + decoder.cumulationSize());
						break;
					}
					System.out.println("####Handle HerokuRawSocketEvent");
					while(ev.content.readable())
					{
						Event tmpev = EventDispatcher.getSingletonInstance().parse(ev.content);
						EventDispatcher.getSingletonInstance().dispatch(tmpev);
					}
				}
		    }
		}
	}
	
	@ChannelPipelineCoverage("one")
	class SocketResponseHandler extends SimpleChannelUpstreamHandler
	{
		private int hash;
		
		public SocketResponseHandler(int hash)
        {
	        this.hash = hash;
        }
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
		    //
		}
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			closeChannel(hash);
		}
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
		    Object msg = e.getMessage();
		    if(msg instanceof ChannelBuffer)
		    {
		    	ChannelBuffer buf = (ChannelBuffer) msg;
		    	HTTPChunkEvent ev = new HTTPChunkEvent();
		    	ev.setHash(hash);
		    	ev.content = new byte[buf.readableBytes()];
		    	buf.readBytes(ev.content);
		    	handler.offer(ev, true);
		    }
		    handleWriteQueue();
		}
	}

	@Override
    public void run()
    {
		long now = System.currentTimeMillis();
		if (!handler.getEventQueue().isEmpty()
		        && now - lastTouchTime > 10000)
		{
			logger.error("Too long time since last request handled.");
			closeAllClient();
			touch();
		}
	    
    }
}
