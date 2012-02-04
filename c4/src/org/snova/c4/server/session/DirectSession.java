package org.snova.c4.server.session;

import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;
import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import org.arch.event.http.HTTPResponseEvent;
import org.arch.event.misc.CompressEventV2;
import org.arch.event.misc.CompressorType;
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
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.SequentialChunkEvent;
import org.snova.c4.common.event.TransactionCompleteEvent;
import org.snova.c4.server.service.EventService;
import org.snova.framework.config.SimpleSocketAddress;

public class DirectSession extends Session
{
	protected ChannelFuture	                                        currentChannelFuture;
	protected static Map<SimpleSocketAddress, Queue<ChannelFuture>>	externalHostsToChannelFutures	= new ConcurrentHashMap<SimpleSocketAddress, Queue<ChannelFuture>>();
	private boolean	                                                chunked;
	private boolean	                                                isHttps;
	private Map<Integer, SequentialChunkEvent>	                    seqChunkTable	              = new HashMap<Integer, SequentialChunkEvent>();
	private int	                                                    waitingChunkSequence;
	private AtomicInteger	                                        writeSequence	              = new AtomicInteger(
	                                                                                                      0);
	
	public DirectSession(SessionManager sm)
	{
		super(sm);
	}
	
	public static void releaseExternalConnections()
	{
		for (Queue<ChannelFuture> conns : externalHostsToChannelFutures
		        .values())
		{
			for (ChannelFuture future : conns)
			{
				if (future.getChannel().isConnected())
				{
					future.getChannel().close();
				}
			}
		}
		externalHostsToChannelFutures.clear();
	}
	
	protected void closeRemote(Channel ch, SimpleSocketAddress addr)
	{
		if (ch.isConnected())
		{
			ch.close();
		}
		// if (logger.isDebugEnabled())
		{
			logger.info("Session[" + getID() + "] closed a connection to "
			        + addr);
		}
		// channelTable.remove(ch.getId());
	}
	
	protected void closeRemote()
	{
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
	}
	
	private void closeLocalChannel()
	{
		HTTPConnectionEvent ev = new HTTPConnectionEvent(
		        HTTPConnectionEvent.CLOSED);
		ev.setHash(getID());
		EventService.getInstance(sessionManager.getUserToken()).offer(ev, null);
		sessionManager.removeSession(this);
	}
	
	private void transactionCompelete(Channel ch)
	{
		writeSequence.set(0);
		TransactionCompleteEvent ev = new TransactionCompleteEvent();
		ev.setHash(getID());
		EventService.getInstance(sessionManager.getUserToken()).offer(ev, ch);
	}
	
	@Override
	public boolean routine()
	{
		return true;
	}
	
	public void close()
	{
		closeRemote();
	}
	
	protected ChannelFuture onRemoteConnected(ChannelFuture future,
	        HTTPRequestEvent req)
	{
		//if (logger.isInfoEnabled())
		{
			logger.info("Session[" + getID() + "] onRemoteConnected with Host:"
			        + req.getHeader("Host"));
		}
		if (!future.isSuccess())
		{
			logger.error("Session[" + getID()
			        + "] close local connection since connect failed.");
			closeLocalChannel();
			return future;
		}
		if (req.method.equalsIgnoreCase("Connect"))
		{
			HTTPResponseEvent response = new HTTPResponseEvent();
			response.setHash(getID());
			response.statusCode = 200;
			response.addHeader("Connection", "Keep-Alive");
			response.addHeader("Proxy-Connection", "Keep-Alive");
			future.getChannel().getPipeline().remove("decoder");
			EventService.getInstance(sessionManager.getUserToken()).offer(
			        response, future.getChannel());
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
			// unansweredRequestCount.incrementAndGet();
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
		waitingChunkSequence = 0;
		seqChunkTable.clear();
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
		channel.getConfig().setOption("connectTimeoutMillis", 40 * 1000);
		SimpleSocketAddress addr = getRemoteAddress(req);
		// if (logger.isDebugEnabled())
		{
			logger.info("Session[" + getID() + "] connect remote address "
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
	
	protected void sendContent(final ChannelBuffer buf)
	{
		if (currentChannelFuture.getChannel().isConnected())
		{
			currentChannelFuture = currentChannelFuture.getChannel().write(buf);
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
				currentChannelFuture.addListener(new ChannelFutureListener()
				{
					public void operationComplete(final ChannelFuture future)
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
			case C4Constants.EVENT_SEQUNCEIAL_CHUNK_TYPE:
			{
				SequentialChunkEvent sequnce = (SequentialChunkEvent) event;
				ChannelBuffer sent = null;
				synchronized (seqChunkTable)
				{
					seqChunkTable.put(sequnce.sequence, sequnce);
					SequentialChunkEvent chunk = seqChunkTable
					        .remove(waitingChunkSequence);
					while (null != chunk)
					{
						waitingChunkSequence++;
						ChannelBuffer buf = ChannelBuffers
						        .wrappedBuffer(chunk.content);
						if (null == sent)
						{
							sent = buf;
						}
						else
						{
							sent = ChannelBuffers.wrappedBuffer(sent, buf);
						}
						chunk = seqChunkTable.remove(waitingChunkSequence);
					}
					if (null != sent)
					{
						sendContent(sent);
					}
				}
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				ChannelBuffer buf = ChannelBuffers.wrappedBuffer(chunk.content);
				sendContent(buf);
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				logger.info("Session[" + event.getHash()
				        + "] handle connection event.");
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
	
	private  synchronized void writeChunk(ChannelBuffer buf, Channel ch)
	{
		SequentialChunkEvent ev = new SequentialChunkEvent();
		ev.setHash(getID());
		ev.sequence = writeSequence.getAndIncrement();
		ev.content = new byte[buf.readableBytes()];
		buf.readBytes(ev.content);
		if(ev.content.length > 512)
		{
			CompressEventV2 wrap = new CompressEventV2(CompressorType.QUICKLZ, ev);
			wrap.setHash(ev.getHash());
			EventService.getInstance(sessionManager.getUserToken()).offer(wrap, ch);
		}
		else
		{
			EventService.getInstance(sessionManager.getUserToken()).offer(ev, ch);
		}
	}
	
	boolean isTransferEncodingChunked(HttpMessage m)
	{
		List<String> chunked = m
		        .getHeaders(HttpHeaders.Names.TRANSFER_ENCODING);
		if (chunked.isEmpty())
		{
			return false;
		}
		
		for (String v : chunked)
		{
			if (v.equalsIgnoreCase(HttpHeaders.Values.CHUNKED))
			{
				return true;
			}
		}
		return false;
	}
	
	private void writeLocal(Object obj, Channel ch)
	{
		if (obj instanceof HttpResponse)
		{
			HttpResponse res = (HttpResponse) obj;
			chunked = isTransferEncodingChunked(res);
			ChannelBuffer buf = buildResponseBuffer(res);
			writeChunk(buf, ch);
		}
		else if (obj instanceof HttpChunk)
		{
			HttpChunk chunk = (HttpChunk) obj;
			ChannelBuffer sent = null;
			if (chunked)
			{
				if (chunk.isLast())
				{
					chunked = false;
					if (chunk instanceof HttpChunkTrailer)
					{
						ChannelBuffer trailer = ChannelBuffers.dynamicBuffer();
						trailer.writeByte((byte) '0');
						trailer.writeByte('\r');
						trailer.writeByte('\n');
						// encodeTrailingHeaders(trailer, (HttpChunkTrailer)
						// chunk);
						try
						{
							for (Map.Entry<String, String> h : ((HttpChunkTrailer) chunk)
							        .getHeaders())
							{
								trailer.writeBytes(h.getKey().getBytes("ASCII"));
								trailer.writeByte(':');
								trailer.writeByte(' ');
								trailer.writeBytes(h.getValue().getBytes(
								        "ASCII"));
								trailer.writeByte('\r');
								trailer.writeByte('\n');
							}
						}
						catch (UnsupportedEncodingException e)
						{
							throw (Error) new Error().initCause(e);
						}
						trailer.writeByte('\r');
						trailer.writeByte('\n');
						sent = trailer;
					}
					else
					{
						sent = ChannelBuffers.copiedBuffer("0\r\n\r\n",
						        CharsetUtil.US_ASCII);
					}
				}
				else
				{
					ChannelBuffer content = chunk.getContent();
					int contentLength = content.readableBytes();
					
					sent = wrappedBuffer(
					        copiedBuffer(Integer.toHexString(contentLength),
					                CharsetUtil.US_ASCII),
					        wrappedBuffer("\r\n".getBytes()), content.slice(
					                content.readerIndex(), contentLength),
					        wrappedBuffer("\r\n".getBytes()));
				}
			}
			else
			{
				if (chunk.isLast())
				{
					sent = null;
				}
				else
				{
					sent = chunk.getContent();
				}
			}
			if (null != sent)
			{
				writeChunk(sent, ch);
			}
			else
			{
				writeChunk(ChannelBuffers.EMPTY_BUFFER, ch);
			}
		}
		else if (obj instanceof ChannelBuffer)
		{
			ChannelBuffer buf = (ChannelBuffer) obj;
			writeChunk(buf, ch);
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
			if (null != relaySession)
			{
				logger.error(
				        "Session["
				                + relaySession.getID()
				                + "] exceptionCaught in DirectRemoteChannelResponseHandler for "
				                + remoteAddress, e.getCause());
			}
			
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
				ChannelBuffer buf = (ChannelBuffer) obj;
				relaySession.writeLocal(buf, ctx.getChannel());
				return;
			}
			else
			{
				logger.error("Unexpected message type:"
				        + obj.getClass().getName());
				return;
			}
			if (null != messageToWrite)
			{
				relaySession.writeLocal(messageToWrite, ctx.getChannel());
				if (writeEndBuffer)
				{
					unanwsered = false;
					// relaySession.unansweredRequestCount.decrementAndGet();
					relaySession.writeLocal(ChannelBuffers.EMPTY_BUFFER,
					        ctx.getChannel());
					relaySession.transactionCompelete(ctx.getChannel());
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
						relaySession.closeLocalChannel();
					}
				}
			}
		}
	}
	
}
