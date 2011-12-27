/**
 * 
 */
package org.snova.heroku.client.handler;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

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
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.heroku.client.connection.ProxyConnection;
import org.snova.heroku.client.connection.ProxyConnectionManager;

/**
 * @author qiyingwang
 * 
 */
public class ProxySession
{
	protected static Logger	       logger	          = LoggerFactory
	                                                          .getLogger(ProxySession.class);
	private ProxyConnectionManager	connectionManager	= ProxyConnectionManager
	                                                          .getInstance();
	private ProxyConnection	       connection	      = null;
	
	private Integer	               sessionID;
	private Channel	               localHTTPChannel;
	// private HTTPRequestEvent proxyEvent;
	private boolean	               isHttps;
	private ProxySessionStatus	   status	          = ProxySessionStatus.INITED;
	
	private ChannelFuture	       writeFuture;
	private ChannelFutureListener	writeFutureListener;
	private List<ChannelBuffer>	   queuedChunk;
	
	private List<ChannelBuffer> getQueuedChunks()
	{
		if (null == queuedChunk)
		{
			queuedChunk = new LinkedList<ChannelBuffer>();
		}
		return queuedChunk;
	}
	
	public ProxySession(Integer id, Channel localChannel)
	{
		this.sessionID = id;
		this.localHTTPChannel = localChannel;
	}
	
	public ProxySessionStatus getStatus()
	{
		return status;
	}
	
	private HTTPRequestEvent cloneHeaders(HTTPRequestEvent event)
	{
		HTTPRequestEvent newEvent = new HTTPRequestEvent();
		newEvent.method = event.method;
		newEvent.url = event.url;
		newEvent.headers = new ArrayList<KeyValuePair<String, String>>();
		for (KeyValuePair<String, String> header : event.getHeaders())
		{
			newEvent.addHeader(header.getName(), header.getValue());
		}
		return newEvent;
	}
	
	public Integer getSessionID()
	{
		return sessionID;
	}
	
	private ProxyConnection getClientConnection(HTTPRequestEvent event)
	{
		if (null == connection)
		{
			connection = connectionManager.getClientConnection(event);
		}
		return connection;
	}
	
	// private ProxyConnection getConcurrentClientConnection(HTTPRequestEvent
	// event)
	// {
	// return connectionManager.getClientConnection(event);
	// }
	
	public void handleResponse(Event res)
	{
		if (res instanceof HTTPResponseEvent)
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Handle received HTTP response event.");
			}
			switch (status)
			{
				case WAITING_CONNECT_RESPONSE:
				{
					if (null != localHTTPChannel
					        && localHTTPChannel.isConnected())
					{
						HttpResponse OK = new DefaultHttpResponse(
						        HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
						localHTTPChannel.write(OK).addListener(
						        new ChannelFutureListener()
						        {
							        @Override
							        public void operationComplete(
							                ChannelFuture future)
							                throws Exception
							        {
								        if (null != localHTTPChannel
								                .getPipeline().get("decoder"))
								        {
									        localHTTPChannel.getPipeline()
									                .remove("decoder");
								        }
								        if (null != localHTTPChannel
								                .getPipeline().get("encoder"))
								        {
									        localHTTPChannel.getPipeline()
									                .remove("encoder");
								        }
								        
							        }
						        });
					}
					status = ProxySessionStatus.PROCEEDING;
					break;
				}
				default:
				{
					logger.error("Can not handle response event at state:"
					        + status);
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
				close();
			}
		}
		else if (res instanceof HTTPChunkEvent)
		{
			HTTPChunkEvent chunk = (HTTPChunkEvent) res;
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				ChannelBuffer content = ChannelBuffers
				        .wrappedBuffer(chunk.content);
				// localHTTPChannel.write(content);
				synchronized (this)
				{
					// localHTTPChannel.write(content);
					if (null == writeFuture || writeFuture.isSuccess())
					{
						writeFuture = localHTTPChannel.write(content);
						writeFutureListener = null;
					}
					else
					{
						getQueuedChunks().add(content);
						if (logger.isDebugEnabled())
						{
							logger.debug("Put chunk into queue since last write is not complete, queue size:"
							        + getQueuedChunks().size());
						}
						if (null == writeFutureListener)
						{
							writeFutureListener = new ChannelFutureListener()
							{
								@Override
								public void operationComplete(
								        ChannelFuture future) throws Exception
								{
									synchronized (ProxySession.this)
									{
										for (ChannelBuffer sent : getQueuedChunks())
										{
											writeFuture = localHTTPChannel
											        .write(sent);
										}
										getQueuedChunks().clear();
									}
								}
							};
						}
					}
					
					// if (writeFutureListenr == null ||
					// writeFutureListenr.expired)
					// {
					// writeFutureListenr = new WriteFutureListenr(null);
					// ChannelFuture future = localHTTPChannel.write(content);
					// future.addListener(writeFutureListenr);
					// }
					// else
					// {
					// writeFutureListenr.next = new
					// WriteFutureListenr(content);
					// writeFutureListenr = writeFutureListenr.next;
					// }
				}
				
			}
			else
			{
				logger.error("Failed to write back content.");
			}
		}
	}
	
	private void handleConnect(HTTPRequestEvent event)
	{
		isHttps = true;
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
	
	public void handle(HTTPRequestEvent event)
	{
		String host = event.getHeader("Host");
		if (null == host)
		{
			SimpleSocketAddress addr = getRemoteAddress(event);
			event.setHeader("Host", addr.toString());
		}
		if (event.method.equalsIgnoreCase(HttpMethod.CONNECT.getName()))
		{
			handleConnect(event);
		}
		else
		{
			// adjustEvent(event);
			status = ProxySessionStatus.PROCEEDING;
			getClientConnection(event).send(event);
		}
	}
	
	public void handle(HTTPChunkEvent event)
	{
		if (null != connection)
		{
			connection.send(event);
		}
		else
		{
			close();
		}
	}
	
	public void close()
	{
		if (null != localHTTPChannel && localHTTPChannel.isOpen())
		{
			localHTTPChannel.close();
		}
		localHTTPChannel = null;
		HTTPConnectionEvent ev = new HTTPConnectionEvent(
		        HTTPConnectionEvent.CLOSED);
		ev.setHash(getSessionID());
		if (null != connection)
		{
			connection.send(ev);
		}
	}
	
	public void routine()
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Session:" + getSessionID() + " status=" + status);
		}
	}
}
