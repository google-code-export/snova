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
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
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
	protected static Logger	logger	= LoggerFactory
	                                       .getLogger(ForwardSession.class);
	
	private String	        host;
	private int	            port;
	private ChannelFuture	remoteFuture;
	
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
	
	protected ChannelFuture getRemoteFuture()
	{
		if (null != remoteFuture && remoteFuture.getChannel().isConnected())
		{
			return remoteFuture;
		}
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("handler", new RemoteChannelResponseHandler());
		
		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		channel.getConfig().setOption("connectTimeoutMillis", 40 * 1000);
		ChannelFuture future = channel
		        .connect(new InetSocketAddress(host, port));
		return future;
	}
	
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent req)
	{
		return new SimpleSocketAddress(host, port);
	}
	
	protected void closeRemote()
	{
		if(null != remoteFuture && remoteFuture.getChannel().isConnected())
		{
			remoteFuture.getChannel().close();
		}
		
	}
	
	protected ChannelFuture onRemoteConnected(ChannelFuture cf,
	        HTTPRequestEvent event)
	{
		ChannelBuffer msg = buildRequestChannelBuffer(event);
		return cf.getChannel().write(msg);
	}
	
	@Override
	public void onEvent(EventHeader header, final Event event)
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Handler event:" + header.type + " in forward session");
		}
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				final HTTPRequestEvent request = (HTTPRequestEvent) event;
				// SimpleSocketAddress addr = getRemoteAddress(request);
				ChannelFuture curFuture = getRemoteFuture();
				if (curFuture.getChannel().isConnected())
				{
					onRemoteConnected(curFuture, request);
				}
				else
				{
					ChannelFutureListener listener = new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							if (future.isSuccess())
							{
								onRemoteConnected(future, request);
							}
							else
							{
								HttpResponse res = new DefaultHttpResponse(
								        HttpVersion.HTTP_1_1,
								        HttpResponseStatus.SERVICE_UNAVAILABLE);
								localChannel.write(res).addListener(
								        ChannelFutureListener.CLOSE);
							}
						}
					};
					curFuture.addListener(listener);
				}
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				final ChannelBuffer buf = ChannelBuffers
				        .wrappedBuffer(chunk.content);
				// final HttpChunk ck = new DefaultHttpChunk(buf);
				if (null != remoteFuture
				        && remoteFuture.getChannel().isConnected())
				{
					remoteFuture.getChannel().write(buf);
				}
				else
				{
					remoteFuture.addListener(new ChannelFutureListener()
					{
						public void operationComplete(final ChannelFuture future)
						        throws Exception
						{
							remoteFuture.getChannel().write(buf);
						}
					});
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
	
	// @ChannelPipelineCoverage("one")
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
			logger.error("exceptionCaught in RemoteChannelResponseHandler",
			        e.getCause());
		}
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object obj = e.getMessage();
			if (obj instanceof ChannelBuffer)
			{
				if (null != localChannel && localChannel.isConnected())
				{
					localChannel.write(obj);
				}
				else
				{
					logger.error("Local browser channel is not connected.");
					closeLocalChannel();
					closeRemote();
				}
			}
			else
			{
				logger.error("Unexpected message type:"
				        + obj.getClass().getName());
			}
		}
	}
}
