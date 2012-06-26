/**
 * 
 */
package org.snova.c4.client.handler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPErrorEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.connection.ProxyConnection;
import org.snova.c4.client.connection.ProxyConnectionManager;
import org.snova.c4.common.event.SequentialChunkEvent;
import org.snova.c4.common.event.TransactionCompleteEvent;
import org.snova.framework.config.SimpleSocketAddress;

/**
 * @author qiyingwang
 * 
 */
public class ProxySession
{
	protected static Logger	                   logger	            = LoggerFactory
	                                                                        .getLogger(ProxySession.class);
	private ProxyConnectionManager	           connectionManager	= ProxyConnectionManager
	                                                                        .getInstance();
	private ProxyConnection	                   connection	        = null;
	
	private Integer	                           sessionID;
	private Channel	                           localHTTPChannel;
	private ProxySessionStatus	               status	            = ProxySessionStatus.INITED;
	
	private ChannelFuture	                   writeFuture;
	private LinkedList<ChannelBuffer>	       restChunkList	    = new LinkedList<ChannelBuffer>();
	private Map<Integer, SequentialChunkEvent>	seqChunkTable	    = new HashMap<Integer, SequentialChunkEvent>();
	private int	                               waitingChunkSequence	= 0;
	private boolean	                           closeAfterFinish	    = false;
	private AtomicInteger	                   sequence	            = new AtomicInteger(
	                                                                        0);
	private long	                           touchTime;
	private ChannelFutureListener	           finishListener	    = new ChannelFutureListener()
	                                                                {
		                                                                @Override
		                                                                public void operationComplete(
		                                                                        ChannelFuture future)
		                                                                        throws Exception
		                                                                {
			                                                                synchronized (restChunkList)
			                                                                {
				                                                                if (!restChunkList
				                                                                        .isEmpty())
				                                                                {
					                                                                ChannelBuffer tmp = restChunkList
					                                                                        .removeFirst();
					                                                                writeFuture = localHTTPChannel
					                                                                        .write(tmp);
					                                                                writeFuture
					                                                                        .addListener(finishListener);
				                                                                }
			                                                                }
			                                                                if (restChunkList
			                                                                        .isEmpty()
			                                                                        && closeAfterFinish)
			                                                                {
				                                                                close();
			                                                                }
		                                                                }
	                                                                };
	
	private ChannelFutureListener	           seqFinishListener	= new ChannelFutureListener()
	                                                                {
		                                                                @Override
		                                                                public void operationComplete(
		                                                                        ChannelFuture future)
		                                                                        throws Exception
		                                                                {
			                                                                completeSequenceChunk();
		                                                                }
	                                                                };
	
	public ProxySession(Integer id, Channel localChannel)
	{
		this.sessionID = id;
		this.localHTTPChannel = localChannel;
		touch();
	}
	
	private void completeSequenceChunk()
	{
		synchronized (seqChunkTable)
		{
			if(null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				if (!seqChunkTable.isEmpty())
				{
					SequentialChunkEvent chunk = seqChunkTable
					        .remove(waitingChunkSequence);
					if (null == chunk)
					{
						if(seqChunkTable.size() >= 500)
						{
							logger.error("#Close channel since too many(500) waiting chunks before chunk:" + waitingChunkSequence + " received.");
							seqChunkTable.clear();
							localHTTPChannel.close();
						}
						if(logger.isDebugEnabled())
						{
							logger.debug("Session[" + getSessionID() + "] expecte sequence chunk:" + waitingChunkSequence);
						}
						return;
					}
					if(logger.isDebugEnabled())
					{
						logger.debug("Session[" + getSessionID() + "] write sequence chunk:" + waitingChunkSequence + " with size:" + chunk.content.length);
					}
					waitingChunkSequence++;
					ChannelBuffer buf = ChannelBuffers.wrappedBuffer(chunk.content);
					writeFuture = localHTTPChannel.write(buf);
					writeFuture.addListener(seqFinishListener);
					return;
				}
			}
			else
			{
				close();
				return;
			}
		}
		if (seqChunkTable.isEmpty() && closeAfterFinish)
		{
			close();
		}
	}
	
	private void touch()
	{
		touchTime = System.currentTimeMillis();
	}
	
	public ProxySessionStatus getStatus()
	{
		return status;
	}
	
	public Integer getSessionID()
	{
		return sessionID;
	}
	
	public ProxyConnection getProxyConnection()
	{
		return connection;
	}
	
	private ProxyConnection getClientConnection(HTTPRequestEvent event)
	{
		if (null == connection)
		{
			connection = connectionManager.getClientConnection(event);
		}
		return connection;
	}
	
	private Object[] buildHttpResponse(HTTPResponseEvent ev)
	{
		if (null == ev)
		{
			return new Object[] { new DefaultHttpResponse(HttpVersion.HTTP_1_1,
			        HttpResponseStatus.REQUEST_TIMEOUT) };
		}
		
		int status = ev.statusCode;
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
		        HttpResponseStatus.valueOf(status));
		response.addHeader("Proxy-Connection", "Close");
		response.addHeader("Connection", "Close");
		
		List<KeyValuePair<String, String>> headers = ev.getHeaders();
		for (KeyValuePair<String, String> header : headers)
		{
			response.addHeader(header.getName(), header.getValue());
		}
		// response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, ""
		// + ev.content.readableBytes());
		
		if (ev.content.readable())
		{
			ChannelBuffer bufer = ChannelBuffers.wrappedBuffer(
			        ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
			// response.setChunked(false);
			HttpChunk chunk = new DefaultHttpChunk(bufer);
			return new Object[] { response, chunk };
			// response.setContent(bufer);
		}
		return new Object[] { response };
	}
	
	public void handleResponse(final Event res)
	{
		touch();
		doHandleResponse(res);
	}
	
	public void doHandleResponse(Event res)
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Session[" + getSessionID()
			        + "] handle received HTTP response event:"
			        + res.getClass().getName() + " at thread:"
			        + Thread.currentThread().getName());
		}
		if (res instanceof HTTPResponseEvent)
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Session[" + getSessionID() + "] handle response.");
				logger.debug(res.toString());
			}
			switch (status)
			{
				case WAITING_CONNECT_RESPONSE:
				{
					if (null != localHTTPChannel
					        && localHTTPChannel.isConnected())
					{
						String msg = "HTTP/1.1 200 Connection established\r\n"
						        + "Connection: Keep-Alive\r\n"
						        + "Proxy-Connection: Keep-Alive\r\n\r\n";
						localHTTPChannel.getPipeline().remove("decoder");
						localHTTPChannel.getPipeline().remove("encoder");
						localHTTPChannel.write(ChannelBuffers.wrappedBuffer(msg
						        .getBytes()));
						
					}
					status = ProxySessionStatus.PROCEEDING;
					break;
				}
				default:
				{
					status = ProxySessionStatus.TRANSACTION_COMPELETE;
					Object[] writeObjs = buildHttpResponse((HTTPResponseEvent) res);
					for (Object obj : writeObjs)
					{
						localHTTPChannel.write(obj);
					}
					break;
				}
			}
		}
		else if (res instanceof HTTPErrorEvent)
		{
			HTTPErrorEvent error = (HTTPErrorEvent) res;
			close();
		}
		else if (res instanceof HTTPConnectionEvent)
		{
			HTTPConnectionEvent conn = (HTTPConnectionEvent) res;
			if (conn.status == HTTPConnectionEvent.CLOSED)
			{
				// status = ProxySessionStatus.PROCEEDING;
				if (null != writeFuture && !writeFuture.isDone())
				{
					closeAfterFinish = true;
					//writeFuture.addListener(ChannelFutureListener.CLOSE);
				}
				else
				{
					close();
				}
				
			}
		}
		else if (res instanceof HTTPChunkEvent)
		{
			// status = ProxySessionStatus.PROCEEDING;
			HTTPChunkEvent chunk = (HTTPChunkEvent) res;
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				ChannelBuffer content = ChannelBuffers
				        .wrappedBuffer(chunk.content);
				synchronized (restChunkList)
				{
					if (writeFuture != null && !writeFuture.isDone())
					{
						restChunkList.add(content);
						if (logger.isDebugEnabled())
						{
							logger.debug("Add content in ready list:"
							        + restChunkList.size());
						}
						// localHTTPChannel.write(content);
						writeFuture.addListener(finishListener);
					}
					else
					{
						writeFuture = localHTTPChannel.write(content);
					}
				}
			}
			else
			{
				logger.error("Failed to write back content.");
			}
		}
		else if (res instanceof SequentialChunkEvent)
		{
			// status = ProxySessionStatus.PROCEEDING;
			SequentialChunkEvent chunk = (SequentialChunkEvent) res;
			if (logger.isDebugEnabled())
			{
				logger.debug("Session[" + getSessionID()
				        + "] received sequence chunk:" + chunk.sequence);
			}
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				synchronized (seqChunkTable)
				{
					seqChunkTable.put(chunk.sequence, chunk);
				}
				if (writeFuture != null && !writeFuture.isDone())
				{
					if (logger.isDebugEnabled())
					{
						logger.debug("Add content in ready table:"
						        + seqChunkTable.size());
					}
					// localHTTPChannel.write(content);
					writeFuture.addListener(seqFinishListener);
				}
				else
				{
					completeSequenceChunk();
					// writeFuture = localHTTPChannel.write(content);
				}
			}
			else
			{
				close();
				logger.error("Failed to write back content.");
			}
		}
		else if (res instanceof TransactionCompleteEvent)
		{
			status = ProxySessionStatus.TRANSACTION_COMPELETE;
		}
	}
	
	private void handleConnect(HTTPRequestEvent event)
	{
		touch();
		// isRequestSent = true;
		status = ProxySessionStatus.WAITING_CONNECT_RESPONSE;
		getClientConnection(event).send(event);
	}
	
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent request)
	{
		String host = request.getHeader("Host");
		if (null == host)
		{
			String url = request.url;
			if (url.startsWith("http://"))
			{
				url = url.substring(7);
				int next = url.indexOf("/");
				host = url.substring(0, next);
			}
			else
			{
				host = url;
			}
		}
		int index = host.indexOf(":");
		int port = 80;
		if (request.method.equalsIgnoreCase("Connect"))
		{
			port = 443;
		}
		String hostValue = host;
		if (index > 0)
		{
			hostValue = host.substring(0, index).trim();
			port = Integer.parseInt(host.substring(index + 1).trim());
		}
		if (logger.isDebugEnabled())
		{
			logger.debug("Get remote address " + hostValue + ":" + port);
		}
		return new SimpleSocketAddress(hostValue, port);
	}
	
	public synchronized void handle(HTTPRequestEvent event)
	{
		touch();
		clearStatus();
		String host = event.getHeader("Host");
		if (null == host)
		{
			SimpleSocketAddress addr = getRemoteAddress(event);
			host = addr.toString();
			event.setHeader("Host", host);
		}
		
		// event.removeHeader("Proxy-Connection");
		// event.setHeader("Connection", "Close");
		
		if (event.method.equalsIgnoreCase(HttpMethod.CONNECT.getName()))
		{
			handleConnect(event);
			// lastProxyEvent = event;
		}
		else
		{
			if (event.url.startsWith("http://" + host))
			{
				int start = "http://".length();
				int end = event.url.indexOf("/", start);
				event.url = event.url.substring(end);
			}
			status = ProxySessionStatus.WAITING_RESPONSE;
			// isRequestSent = true;
			getClientConnection(event).send(event);
			
			if (logger.isDebugEnabled())
			{
				logger.debug("Session[" + getSessionID() + "] sent request.");
				logger.debug(event.toString());
			}
		}
	}
	
	public synchronized void handle(HTTPChunkEvent event)
	{
		touch();
		if (null != connection)
		{
			// event.setHash(lastProxyEvent.getHash());
			if (event.content.length > 0)
			{
				SequentialChunkEvent chunk = new SequentialChunkEvent();
				chunk.setHash(getSessionID());
				chunk.content = event.content;
				chunk.sequence = sequence.getAndIncrement();
				connection.send(chunk);
			}
		}
		else
		{
			close();
		}
	}
	
	private void clearStatus()
	{
		restChunkList.clear();
		seqChunkTable.clear();
		waitingChunkSequence = 0;
		writeFuture = null;
		closeAfterFinish = false;
		sequence.set(0);
	}
	
	public void close()
	{
		if(ProxySessionManager.getInstance().getProxySession(getSessionID()) == null)
		{
			return;
		}
		boolean closeActionTriggerd = false;
		if (status.equals(ProxySessionStatus.WAITING_RESPONSE))
		{
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				HttpResponse res = new DefaultHttpResponse(
				        HttpVersion.HTTP_1_1,
				        HttpResponseStatus.REQUEST_TIMEOUT);
				logger.error("Session["
				        + getSessionID()
				        + "] send fake 408 to browser since session closed while no response sent.");
				localHTTPChannel.write(res).addListener(
				        ChannelFutureListener.CLOSE);
				closeActionTriggerd = true;
			}
		}
		else if (status.equals(ProxySessionStatus.WAITING_CONNECT_RESPONSE))
		{
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				HttpResponse res = new DefaultHttpResponse(
				        HttpVersion.HTTP_1_1,
				        HttpResponseStatus.SERVICE_UNAVAILABLE);
				localHTTPChannel.write(res).addListener(
				        ChannelFutureListener.CLOSE);
				closeActionTriggerd = true;
			}
		}
		if (!closeActionTriggerd && null != localHTTPChannel
		        && localHTTPChannel.isConnected())
		{
			if(localHTTPChannel.isConnected())
			{
				localHTTPChannel.close();
			}
			
		}
		localHTTPChannel = null;
		HTTPConnectionEvent ev = new HTTPConnectionEvent(
		        HTTPConnectionEvent.CLOSED);
		ev.setHash(getSessionID());
		if (null != connection)
		{
			connection.send(ev);
		}
		status = ProxySessionStatus.SESSION_COMPLETED;
		ProxySessionManager.getInstance().removeSession(this);
		clearStatus();
	}
	
	public ProxySessionStatus routine()
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Session:" + getSessionID() + " status=" + status);
		}
		long now = System.currentTimeMillis();
		if (now - touchTime >= C4ClientConfiguration.getInstance()
		        .getSessionIdleTimeout())
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Session:" + getSessionID()
				        + " will be closed since in idle too long:"
				        + (now - touchTime) + "ms");
			}
			close();
		}
		return status;
	}
}
