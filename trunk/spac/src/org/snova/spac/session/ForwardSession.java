/**
 * 
 */
package org.snova.spac.session;

import java.net.InetSocketAddress;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;

/**
 * @author wqy
 *
 */
public class ForwardSession extends Session
{
	protected static Logger	              logger	       = LoggerFactory
            .getLogger(ForwardSession.class);
	
	private String host;
	private int port;
	
	protected Channel remoteChannel;
	
	public ForwardSession(String addr)
	{
		String[] ss = addr.trim().split(":");
		host = ss[0].trim();
		port = Integer.parseInt(ss[1].trim());
	}
	@Override
    public SessionType getType()
    {
	    return SessionType.FORWARD;
    }
	
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent req)
	{
		return new SimpleSocketAddress(host, port);
	}
	
	protected Channel getRemoteChannel(String host, int port)
	{
		if(logger.isDebugEnabled())
		{
			logger.debug("Get remote channel with address " + host + ":" + port);
		}
		if(null != remoteChannel && remoteChannel.isConnected())
		{
			return remoteChannel;
		}
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("handler", new RemoteChannelResponseHandler());
		SocketChannel channel = getClientSocketChannelFactory().newChannel(pipeline);
		ChannelFuture future = channel.connect(
		        new InetSocketAddress(host, port))
		        .awaitUninterruptibly();
		if (!future.isSuccess())
		{
			logger.error("Failed to connect forward address.", future.getCause());
			return null;
		}
		if(logger.isDebugEnabled())
		{
			logger.debug("Connect remote channel with address " + host + ":" + port + " success!");
		}
		return channel;
	}
	
	protected void closeRemote()
	{
		if(null != remoteChannel)
		{
			remoteChannel.close();
			remoteChannel = null;
		}
	}

	@Override
    public void onEvent(EventHeader header, Event event)
    {
		if(logger.isDebugEnabled())
		{
			logger.debug("Handler event:" + header.type + " in forward session");
		}
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				SimpleSocketAddress addr = getRemoteAddress((HTTPRequestEvent) event);
				remoteChannel = getRemoteChannel(addr.host, addr.port);
				if(null == remoteChannel)
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
					localChannel.write(res);
				}
				ChannelBuffer msg = buildRequestChannelBuffer((HTTPRequestEvent) event);
				remoteChannel.write(msg);
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				ChannelBuffer buf = ChannelBuffers.wrappedBuffer(chunk.content);
				if(null != remoteChannel)
				{
					remoteChannel.write(buf);
				}
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				HTTPConnectionEvent ev = (HTTPConnectionEvent) event;
				if (ev.status == HTTPConnectionEvent.CLOSED)
				{
					closeRemote();
				}
				break;
			}
			default:
			{
				logger.error("Unexpected event type:" + header.type);
				break;
			}
		}  
    }
	
	@ChannelPipelineCoverage("one")
	class RemoteChannelResponseHandler extends SimpleChannelUpstreamHandler
	{
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Connection closed.");
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			logger.error("exceptionCaught in RemoteChannelResponseHandler", e.getCause());
		}
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
		    Object obj = e.getMessage();
		    if(obj instanceof ChannelBuffer)
		    {
		    	if(null != localChannel&&localChannel.isConnected())
		    	{
		    		localChannel.write(obj);
		    	}
		    	else
		    	{
		    		logger.error("Local browser channel is not connected.");
		    		closeRemote();
		    	}
		    }
		    else
		    {
		    	logger.error("Unexpected message type:" + obj.getClass().getName());
		    }
		}
	}
}
