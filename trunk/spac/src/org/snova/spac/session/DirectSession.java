package org.snova.spac.session;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.snova.framework.config.SimpleSocketAddress;

public class DirectSession extends Session
{
	protected ChannelFuture	                                        currentChannelFuture;
	protected static Map<SimpleSocketAddress, Queue<ChannelFuture>>	externalHostsToChannelFutures	= new ConcurrentHashMap<SimpleSocketAddress, Queue<ChannelFuture>>();
	protected Map<Integer, Channel>	                                channelTable	              = new org.jboss.netty.util.internal.ConcurrentHashMap<Integer, Channel>();
	private boolean	                                                isHttps;
	private AtomicInteger	                                        unansweredRequestCount	      = new AtomicInteger(
	                                                                                                      0);
	
	public DirectSession()
	{
		// super("127.0.0.1:0");
	}
	
	@Override
	public SessionType getType()
	{
		return SessionType.DIRECT;
	}
	
	protected void closeRemote(Channel ch, SimpleSocketAddress addr)
	{
		if (ch.isConnected())
		{
			ch.close();
		}
		if (logger.isDebugEnabled())
		{
			logger.debug("Session[" + getID() + "] closed a connection to "
			        + addr);
		}
		channelTable.remove(ch.getId());
	}
	
	protected void closeRemote()
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Session[" + getID() + "] close all remote connection");
		}
		for (Channel ch : channelTable.values())
		{
			if (ch.isConnected())
			{
				// ch.close();
			}
			
		}
		channelTable.clear();
		if (null != currentChannelFuture
		        && currentChannelFuture.getChannel().isConnected())
		{
			if (isHttps)
			{
				currentChannelFuture.getChannel().close();
			}
			else
			{
				DirectRemoteChannelResponseHandler handler = currentChannelFuture
				        .getChannel().getPipeline()
				        .get(DirectRemoteChannelResponseHandler.class);
				if (handler.unanwsered)
				{
					currentChannelFuture.getChannel().close();
				}
			}
		}
		// externalHostsToChannelFutures.clear();
	}
	
	protected ChannelFuture onRemoteConnected(ChannelFuture future,
	        HTTPRequestEvent req)
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Session[" + getID() + "] onRemoteConnected");
		}
		if (!future.isSuccess())
		{
			closeLocalChannel();
			return future;
		}
		if (req.method.equalsIgnoreCase("Connect"))
		{
			String msg = "HTTP/1.1 200 Connection established\r\n"
			        + "Connection: Keep-Alive\r\n"
			        + "Proxy-Connection: Keep-Alive\r\n\r\n";
			future.getChannel().getPipeline().remove("decoder");
			if (null != localChannel && localChannel.isConnected())
			{
				removeCodecHandler(localChannel);
				localChannel
				        .write(ChannelBuffers.wrappedBuffer(msg.getBytes()));
			}
			else
			{
				future.getChannel().close();
			}
			return future;
		}
		else
		{
			ChannelBuffer msg = buildRequestChannelBuffer(req);
			if (logger.isDebugEnabled())
			{
				logger.debug("Direct session[" + getID() + "] send request:\n"
				        + msg.toString(Charset.forName("UTF-8")));
			}
			unansweredRequestCount.incrementAndGet();
			DirectRemoteChannelResponseHandler handler = future.getChannel()
			        .getPipeline()
			        .get(DirectRemoteChannelResponseHandler.class);
			handler.unanwsered = true;
			return future.getChannel().write(msg);
		}
		
	}
	
	protected ChannelFuture getChannelFuture(HTTPRequestEvent req)
	{
		ChannelFuture future = null;
		synchronized (externalHostsToChannelFutures)
		{
			Queue<ChannelFuture> futures = externalHostsToChannelFutures
			        .get(getRemoteAddressFromRequestEvent(req));
			if (futures != null)
			{
				do
				{
					if (futures.isEmpty())
					{
						break;
					}
					ChannelFuture cf = futures.remove();
					
					if (cf != null && cf.isSuccess()
					        && !cf.getChannel().isConnected())
					{
						// In this case, the future successfully connected at
						// one
						// time, but we're no longer connected. We need to
						// remove
						// the
						// channel and open a new one.
						continue;
					}
					future = cf;
					break;
				} while (true);
			}
		}
		if (null == future)
		{
			future = newRemoteChannelFuture(req);
		}
		DirectRemoteChannelResponseHandler handler = future.getChannel()
		        .getPipeline().get(DirectRemoteChannelResponseHandler.class);
		handler.relaySession = this;
		currentChannelFuture = future;
		return future;
	}
	
	protected static void onChannelAvailable(
	        final SimpleSocketAddress hostAndPortKey, final ChannelFuture cf)
	{
		synchronized (externalHostsToChannelFutures)
		{
			Queue<ChannelFuture> futures = externalHostsToChannelFutures
			        .get(hostAndPortKey);
			
			if (futures == null)
			{
				futures = new LinkedList<ChannelFuture>();
				externalHostsToChannelFutures.put(hostAndPortKey, futures);
			}
			futures.add(cf);
		}
	}
	
	protected ChannelFuture newRemoteChannelFuture(HTTPRequestEvent req)
	{
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new HttpResponseDecoder());
		DirectRemoteChannelResponseHandler handler = new DirectRemoteChannelResponseHandler();
		handler.remoteAddress = getRemoteAddressFromRequestEvent(req);
		isHttps = handler.isHttps = req.method.equalsIgnoreCase("Connect");
		pipeline.addLast("handler", handler);
		
		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		synchronized (channelTable)
		{
			channelTable.put(channel.getId(), channel);
		}
		channel.getConfig().setOption("connectTimeoutMillis", 40 * 1000);
		SimpleSocketAddress addr = getRemoteAddress(req);
		if (logger.isDebugEnabled())
		{
			logger.debug("Session[" + getID() + "] connect remote address "
			        + addr);
		}
		ChannelFuture future = channel.connect(new InetSocketAddress(addr.host,
		        addr.port));
		handler.remoteChannelFuture = future;
		return future;
	}
	
	// @Override
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent req)
	{
		return getRemoteAddressFromRequestEvent(req);
	}
	
	@Override
	public void onEvent(EventHeader header, final Event event)
	{
		
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				final HTTPRequestEvent req = (HTTPRequestEvent) event;
				
				ChannelFuture future = getChannelFuture(req);
				if (future.getChannel().isConnected())
				{
					// future.getChannel().write(msgcontent);
					onRemoteConnected(future, req);
				}
				else
				{
					future.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture cf)
						        throws Exception
						{
							onRemoteConnected(cf, req);
						}
					});
				}
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				final ChannelBuffer buf = ChannelBuffers
				        .wrappedBuffer(chunk.content);
				if (currentChannelFuture.getChannel().isConnected())
				{
					currentChannelFuture = currentChannelFuture.getChannel()
					        .write(buf);
				}
				else
				{
					if (currentChannelFuture.isSuccess())
					{
						logger.error("####Session["
						        + getID()
						        + "] current remote connection already closed, while chunk size:"
						        + buf.readableBytes());
						closeLocalChannel();
					}
					else
					{
						currentChannelFuture
						        .addListener(new ChannelFutureListener()
						        {
							        public void operationComplete(
							                final ChannelFuture future)
							                throws Exception
							        {
								        if (future.isSuccess())
								        {
									        future.getChannel().write(buf);
								        }
								        else
								        {
									        logger.error("Remote connection closed.");
									        closeLocalChannel();
								        }
							        }
						        });
					}
					
				}
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				HTTPConnectionEvent ev = (HTTPConnectionEvent) event;
				if (ev.status == HTTPConnectionEvent.CLOSED)
				{
					if (null != currentChannelFuture
					        && !currentChannelFuture.isDone())
					{
						currentChannelFuture
						        .addListener(new ChannelFutureListener()
						        {
							        
							        @Override
							        public void operationComplete(
							                ChannelFuture future)
							                throws Exception
							        {
								        closeRemote();
							        }
						        });
					}
					else
					{
						closeRemote();
					}
					
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
	
	static class DirectRemoteChannelResponseHandler extends
	        SimpleChannelUpstreamHandler
	{
		private SimpleSocketAddress	remoteAddress;
		private ChannelFuture		remoteChannelFuture;
		// private Channel relayChannel;
		private DirectSession		relaySession;
		
		private boolean		        keepAlive	= false;
		private boolean		        isHttps;
		private boolean		        closeEndsResponseBody;
		private boolean		        unanwsered	= false;
		
		private boolean closeEndsResponseBody(final HttpResponse res)
		{
			String cl = res.getHeader(HttpHeaders.Names.CONTENT_LENGTH);
			if (cl != null)
			{
				return false;
			}
			final String te = res
			        .getHeader(HttpHeaders.Names.TRANSFER_ENCODING);
			if (te != null && te.equalsIgnoreCase(HttpHeaders.Values.CHUNKED))
			{
				return false;
			}
			return true;
		}
		
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			// if (logger.isDebugEnabled())
			// {
			// logger.debug("Connection closed.");
			// }
			
			relaySession.closeRemote(ctx.getChannel(), remoteAddress);
			if (isHttps)
			{
				relaySession.closeLocalChannel();
			}
			else
			{
				if (unanwsered)
				{
					relaySession.closeLocalChannel();
				}
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			logger.error(
			        "Session["
			                + relaySession.getID()
			                + "] exceptionCaught in DirectRemoteChannelResponseHandler for "
			                + remoteAddress, e.getCause());
		}
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object obj = e.getMessage();
			boolean writeEndBuffer = false;
			Object messageToWrite = null;
			if (obj instanceof HttpResponse)
			{
				if (logger.isDebugEnabled())
				{
					logger.debug("Session[" + relaySession.getID()
					        + "] received direct HTTP response:" + obj);
				}
				HttpResponse response = (HttpResponse) obj;
				String te = response
				        .getHeader(HttpHeaders.Names.TRANSFER_ENCODING);
				if (null != te)
				{
					te = te.trim();
				}
				if (response.isChunked())
				{
					writeEndBuffer = false;
				}
				else
				{
					writeEndBuffer = true;
				}
				messageToWrite = response;
				keepAlive = HttpHeaders.isKeepAlive(response);
				closeEndsResponseBody = closeEndsResponseBody(response);
			}
			else if (obj instanceof HttpChunk)
			{
				
				HttpChunk chunk = (HttpChunk) obj;
				messageToWrite = chunk;
				if (chunk.isLast())
				{
					if (logger.isDebugEnabled())
					{
						logger.debug("Session[" + relaySession.getID()
						        + "] received direct last HTTP chunk.");
					}
					// onChannelAvailable(remoteAddress, remoteChannelFuture);
					writeEndBuffer = true;
				}
				else
				{
					writeEndBuffer = false;
					
				}
			}
			else if (obj instanceof ChannelBuffer)
			{
				if (null != relaySession.localChannel
				        && relaySession.localChannel.isConnected())
				{
					relaySession.localChannel.write(obj);
				}
				else
				{
					logger.error("Local browser channel is not connected.");
					// closeLocalChannel();
					// closeRemote();
					
				}
				return;
			}
			else
			{
				logger.error("Unexpected message type:"
				        + obj.getClass().getName());
				return;
			}
			
			if (null != relaySession.localChannel
			        && relaySession.localChannel.isConnected())
			{
				ChannelFuture writefuture = null;
				if (null != messageToWrite)
				{
					writefuture = relaySession.localChannel
					        .write(messageToWrite);
				}
				if (writeEndBuffer)
				{
					unanwsered = false;
					relaySession.unansweredRequestCount.decrementAndGet();
					
					writefuture = relaySession.localChannel
					        .write(ChannelBuffers.EMPTY_BUFFER);
					if (keepAlive)
					{
						// relaySession.waitingResponse.decrementAndGet();
						onChannelAvailable(remoteAddress, remoteChannelFuture);
					}
					else
					{
						relaySession.closeRemote(ctx.getChannel(),
						        remoteAddress);
					}
					if (closeEndsResponseBody)
					{
						writefuture.addListener(ChannelFutureListener.CLOSE);
					}
					// writefuture.addListener(ChannelFutureListener.CLOSE);
					//
				}
			}
		}
	}
}
