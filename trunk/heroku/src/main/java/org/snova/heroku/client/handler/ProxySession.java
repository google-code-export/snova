/**
 * 
 */
package org.snova.heroku.client.handler;

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
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
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
	protected static Logger logger = LoggerFactory
	        .getLogger(ProxySession.class);
	private ProxyConnectionManager connectionManager = ProxyConnectionManager
	        .getInstance();
	private ProxyConnection connection = null;

	private Integer sessionID;
	private Channel localHTTPChannel;
	// private HTTPRequestEvent proxyEvent;
	private boolean isRequestSent;
	private ProxySessionStatus status = ProxySessionStatus.INITED;

//	private ChannelFuture writeFuture;
//	private ChannelFutureListener writeFutureListener;
//	private List<ChannelBuffer> queuedChunk;
//	private static ChunkIOTask ioTask = null;
//
//	static synchronized ChunkIOTask getChunkIOTask()
//	{
//		if (null == ioTask)
//		{
//			ioTask = new ChunkIOTask();
//			new Thread(ioTask).start();
//		}
//		return ioTask;
//	}
//
//	static class ChunkIOTask implements Runnable
//	{
//		List<Pair<Channel, ChannelBuffer>> writeQueue = new LinkedList<Pair<Channel, ChannelBuffer>>();
//
//		void offer(Pair<Channel, ChannelBuffer> v)
//		{
//			synchronized (this)
//			{
//				writeQueue.add(v);
//				this.notify();
//			}
//		}
//
//		@Override
//		public void run()
//		{
//			while (true)
//			{
//				List<Pair<Channel, ChannelBuffer>> readyList = new LinkedList<Pair<Channel, ChannelBuffer>>();
//				try
//				{
//					synchronized (this)
//					{
//						if (writeQueue.isEmpty())
//						{
//							this.wait(1000);
//						}
//						if (writeQueue.isEmpty())
//						{
//							continue;
//						}
//						readyList.clear();
//						for (Pair<Channel, ChannelBuffer> v : writeQueue)
//						{
//							readyList.add(v);
//						}
//						writeQueue.clear();
//					}
//					for (Pair<Channel, ChannelBuffer> v : readyList)
//					{
//						v.first.write(v.second).awaitUninterruptibly();
//					}
//				}
//				catch (Exception e)
//				{
//					logger.error("Failed to write channel in IP task thread.",
//					        e);
//				}
//			}
//
//		}
//
//	}
//
//	private List<ChannelBuffer> getQueuedChunks()
//	{
//		if (null == queuedChunk)
//		{
//			queuedChunk = new LinkedList<ChannelBuffer>();
//		}
//		return queuedChunk;
//	}

	public ProxySession(Integer id, Channel localChannel)
	{
		this.sessionID = id;
		this.localHTTPChannel = localChannel;
	}

	public ProxySessionStatus getStatus()
	{
		return status;
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

	private Object[] buildHttpResponse(HTTPResponseEvent ev)
	{
		if (null == ev)
		{
			return new Object[]{new DefaultHttpResponse(HttpVersion.HTTP_1_1,
			        HttpResponseStatus.REQUEST_TIMEOUT)};
		}
		
		int status = ev.statusCode;
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
		        HttpResponseStatus.valueOf(status));
		
		List<KeyValuePair<String, String>> headers = ev.getHeaders();
		for (KeyValuePair<String, String> header : headers)
		{
			response.addHeader(header.getName(), header.getValue());
		}
		//response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, ""
		//        + ev.content.readableBytes());
		
		if (ev.content.readable())
		{
			ChannelBuffer bufer = ChannelBuffers.wrappedBuffer(
			        ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
			// response.setChunked(false);
			HttpChunk chunk = new DefaultHttpChunk(bufer);
			return new Object[]{response, chunk};
			//response.setContent(bufer);
		}
		return  new Object[]{response};
	}
	
	public void handleResponse(final Event res)
	{
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
			if(logger.isDebugEnabled())
			{
				logger.debug("Session["  +getSessionID() + "] handle response.");
				logger.debug(res.toString());
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
					status = ProxySessionStatus.PROCEEDING;
					Object[] writeObjs = buildHttpResponse((HTTPResponseEvent) res);
					for(Object obj:writeObjs)
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
				close();
			}
		}
		else if (res instanceof HTTPChunkEvent)
		{
			status = ProxySessionStatus.PROCEEDING;
			HTTPChunkEvent chunk = (HTTPChunkEvent) res;
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				ChannelBuffer content = ChannelBuffers
				        .wrappedBuffer(chunk.content);
				localHTTPChannel.write(content);
			}
			else
			{
				logger.error("Failed to write back content.");
			}
		}
	}

	private void handleConnect(HTTPRequestEvent event)
	{
		isRequestSent = true;
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
			//event.setHeader("Connection", "close");
			// adjustEvent(event);
			if(isRequestSent)
			{
				status = ProxySessionStatus.PROCEEDING;
			}
			else
			{
				status = ProxySessionStatus.WAITING_FIRST_RESPONSE;
			}
			isRequestSent = true;
			getClientConnection(event).send(event);
			if(logger.isDebugEnabled())
			{
				logger.debug("Session["  +getSessionID() + "] sent request.");
				logger.debug(event.toString());
			}
		}
	}

	public synchronized void handle(HTTPChunkEvent event)
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
		if(status.equals(ProxySessionStatus.WAITING_FIRST_RESPONSE))
		{
			if(null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
				        HttpResponseStatus.REQUEST_TIMEOUT);
				logger.error("Session[" + getSessionID() + "] send fake 408 to browser since session closed while no response sent.");
				localHTTPChannel.write(res);
			}
		}
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
