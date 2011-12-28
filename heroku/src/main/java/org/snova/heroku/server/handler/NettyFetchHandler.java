/**
 * 
 */
package org.snova.heroku.server.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author qiyingwang
 *
 */
public class NettyFetchHandler implements Runnable
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private ClientSocketChannelFactory factory;
	private Map<Integer, Channel> channelTable = new ConcurrentHashMap<Integer, Channel>();
	private Map<Channel, Integer> biChannelTable = new ConcurrentHashMap<Channel, Integer>();
	private ServerEventHandler handler;
	private long lastTouchTime = -1;
	public NettyFetchHandler(ServerEventHandler handler)
	{
		this.handler = handler;
		ThreadPoolExecutor workerExecutor = new OrderedMemoryAwareThreadPoolExecutor(
		        10, 0, 0);
		handler.getThreadPool().scheduleAtFixedRate(this, 100, 1000, TimeUnit.SECONDS);
		factory = new NioClientSocketChannelFactory(workerExecutor, workerExecutor);
	}
	
	public long touch()
	{
		long now = System.currentTimeMillis();
		lastTouchTime = now;
		return now;
	}

	
	public void fetch(HTTPChunkEvent event)
	{
		//lastFetchTime = System.
		Channel channel = channelTable.get(event.getHash());
		if (null != channel)
		{
			channel.write(ChannelBuffers.wrappedBuffer(event.content));
		}
		else
		{
			logger.error("No connected connection found for chunk:"
			        + event.getHash());
		}
	}
	
	private ChannelBuffer buildSentBuffer(HTTPRequestEvent ev)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(ev.method).append(" ").append(ev.url).append(" ")
		        .append("HTTP/1.1\r\n");
		for (KeyValuePair<String, String> header : ev.headers)
		{
			buffer.append(header.getName()).append(":")
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
		return ChannelBuffers.wrappedBuffer(msg.getRawBuffer(), 0, msg.readableBytes());
	}
	
	public void handleConnectionEvent(HTTPConnectionEvent ev)
	{
		if (ev.status == HTTPConnectionEvent.CLOSED)
		{
			closeConnection(ev.getHash());
		}
	}

	public void fetch(final HTTPRequestEvent event)
	{
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
		Channel channel = channelTable.get(event.getHash());
		if (null != channel)
		{
			if (channel.isConnected())
			{
				//Object[] attach = (Object[]) key.attachment();
				channel.write(buildSentBuffer(event));
			}
			else
			{
				closeConnection(event.getHash());
			}
		}
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("encoder", new HttpRequestEncoder());
        pipeline.addLast("handler", new SocketResponseHandler());
        final SocketChannel tmp = factory.newChannel(pipeline);
        tmp.connect(new InetSocketAddress(host, port)).addListener(new ChannelFutureListener()
        {
        	@Override
        	public void operationComplete(ChannelFuture future) throws Exception
        	{
        		if(future.isSuccess())
        		{
        			connectionConnected(tmp, event.getHash());
        			tmp.write(buildSentBuffer(event));
        		}
        		else
        		{
        			closeConnection(event.getHash());
        		}
        	}
        });
	}
	
	private void closeAllClient()
	{
		Set<Integer> keys = channelTable.keySet();
		for(Integer k:keys)
		{
			closeConnection(k);
		}
		handler.clearEventQueue();
	}
	
	private void closeConnection(int hash)
    {
		Channel ch = channelTable.remove(hash);
		if(null != ch)
		{
			ch.close();
			biChannelTable.remove(ch);
		}
		
    }
	private void closeConnection(Channel ch)
    {
		ch.close();
		Integer hash = biChannelTable.remove(ch);
		if(null != hash)
		{
			channelTable.remove(hash);
		}
	    
    }
	private void connectionConnected(SocketChannel ch, int hash)
    {
		channelTable.put(hash, ch);
		biChannelTable.put(ch, hash);
	    
    }

	@ChannelPipelineCoverage("one")
	class SocketResponseHandler extends SimpleChannelUpstreamHandler
	{
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			closeConnection(ctx.getChannel());
		}
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
		  	Channel ch = ctx.getChannel();
	    	Integer hash = biChannelTable.get(ch);
	    	if(null == hash)
	    	{
	    		logger.error("No coresspond session found");
	    		return;
	    	}
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
		    else if(msg instanceof HttpResponse)
		    {
		    	HttpResponse response = (HttpResponse) msg;
		    	HTTPResponseEvent ev = new HTTPResponseEvent();
		    	ev.setHash(hash);
	
		    	ev.statusCode = response.getStatus().getCode();
				ChannelBuffer content = response.getContent();
				if (null != content && content.readable())
				{
					int buflen = content.readableBytes();
					ev.content.ensureWritableBytes(content.readableBytes());
					content.readBytes(ev.content.getRawBuffer(),
							ev.content.getWriteIndex(), content.readableBytes());
					ev.content.advanceWriteIndex(buflen);
				}
				for (String name : response.getHeaderNames())
				{
					for (String value : response.getHeaders(name))
					{
						ev.headers
						        .add(new KeyValuePair<String, String>(name, value));
					}
				}
				handler.offer(ev, true);
		    }
		    else if(msg instanceof HttpChunk)
		    {
		    	HttpChunk chunk = (HttpChunk) msg;
		    	HTTPChunkEvent ev = new HTTPChunkEvent();
	    		ev.setHash(hash);
	    		ev.content = new byte[chunk.getContent().readableBytes()];
	    		chunk.getContent().readBytes(ev.content);
	    		handler.offer(ev, true);
		    }
		    else
		    {
		    	logger.error("Unsupported message type:" + msg.getClass().getName());
		    }
		}
	}

	@Override
    public void run()
    {
	    long now = System.currentTimeMillis();
	    if(now - lastTouchTime > 10000)
	    {
	    	closeAllClient();
			touch();
	    }
    }
}
