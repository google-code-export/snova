/**
 * 
 */
package org.snova.c4.client.handler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.arch.event.Event;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.connection.ProxyConnection;
import org.snova.c4.client.connection.ProxyConnectionManager;
import org.snova.c4.common.event.SocketConnectionEvent;
import org.snova.c4.common.event.TCPChunkEvent;
import org.snova.framework.config.SimpleSocketAddress;

/**
 * @author qiyingwang
 * 
 */
public class ProxySession
{
	protected static Logger	            logger	          = LoggerFactory
	                                                              .getLogger(ProxySession.class);
	private ProxyConnectionManager	    connectionManager	= ProxyConnectionManager
	                                                              .getInstance();
	private ProxyConnection	            connection	      = null;
	
	private Integer	                    sessionID;
	private String	                    remoteAddr;
	private Channel	                    localHTTPChannel;
	private ChannelFuture	            writeFuture;
	private ProxySessionStatus	        status	          = ProxySessionStatus.INITED;
	
	private AtomicInteger	            sequence	      = new AtomicInteger(0);
	
	private AtomicInteger	            readSequence	  = new AtomicInteger(0);
	private Map<Integer, TCPChunkEvent>	chunks	          = new ConcurrentHashMap<Integer, TCPChunkEvent>();
	
	public ProxySession(Integer id, Channel localChannel)
	{
		this.sessionID = id;
		this.localHTTPChannel = localChannel;
		touch();
	}
	
	// private void completeSequenceChunk()
	// {
	// synchronized (seqChunkTable)
	// {
	// if(null != localHTTPChannel && localHTTPChannel.isConnected())
	// {
	// if (!seqChunkTable.isEmpty())
	// {
	// TCPChunkEvent chunk = seqChunkTable
	// .remove(waitingChunkSequence);
	// if (null == chunk)
	// {
	// if(seqChunkTable.size() >= 500)
	// {
	// logger.error("#Close channel since too many(500) waiting chunks before chunk:"
	// + waitingChunkSequence + " received.");
	// seqChunkTable.clear();
	// localHTTPChannel.close();
	// }
	// if(logger.isDebugEnabled())
	// {
	// logger.debug("Session[" + getSessionID() + "] expecte sequence chunk:" +
	// waitingChunkSequence);
	// }
	// return;
	// }
	// if(logger.isDebugEnabled())
	// {
	// logger.debug("Session[" + getSessionID() + "] write sequence chunk:" +
	// waitingChunkSequence + " with size:" + chunk.content.length);
	// }
	// waitingChunkSequence++;
	// ChannelBuffer buf = ChannelBuffers.wrappedBuffer(chunk.content);
	// writeFuture = localHTTPChannel.write(buf);
	// writeFuture.addListener(seqFinishListener);
	// return;
	// }
	// }
	// else
	// {
	// close();
	// return;
	// }
	// }
	// if (seqChunkTable.isEmpty() && closeAfterFinish)
	// {
	// close();
	// }
	// }
	
	private void touch()
	{
		// touchTime = System.currentTimeMillis();
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
	
	public void handleResponse(final Event res)
	{
		//touch();
		doHandleResponse(res);
	}
	
	private void writeChunk()
	{
		if (!chunks.isEmpty())
		{
			final TCPChunkEvent chunk = chunks.remove(readSequence.get());
			if (null == chunk)
			{
				return;
			}
			readSequence.addAndGet(1);
			localHTTPChannel.write(ChannelBuffers
			        .wrappedBuffer(chunk.content));
			writeChunk();
//			writeFuture.addListener(new ChannelFutureListener()
//			{
//				@Override
//				public void operationComplete(ChannelFuture future)
//				        throws Exception
//				{
//					writeFuture = 
//				}
//			});
		}
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
		if (res instanceof SocketConnectionEvent)
		{
			SocketConnectionEvent conn = (SocketConnectionEvent) res;
			if (conn.status == SocketConnectionEvent.TCP_CONN_CLOSED)
			{
				if (conn.addr.equalsIgnoreCase(remoteAddr))
				{
					if(null != writeFuture)
					{
						writeFuture.addListener(new ChannelFutureListener()
						{							
							@Override
							public void operationComplete(ChannelFuture future) throws Exception
							{
								close();
							}
						});
					}
					else
					{
						close();
					}
				}
			}
		}
		else if (res instanceof TCPChunkEvent)
		{
			// status = ProxySessionStatus.PROCEEDING;
			TCPChunkEvent chunk = (TCPChunkEvent) res;
			if (logger.isDebugEnabled())
			{
				logger.debug("Session[" + getSessionID()
				        + "] received sequence chunk:" + chunk.sequence);
			}
			if (null != localHTTPChannel && localHTTPChannel.isConnected())
			{
				chunks.put(chunk.sequence, chunk);
				writeChunk();
			}
			else
			{
				close();
				logger.error("Failed to write back content.");
			}
		}
	}
	
	private void handleConnect(HTTPRequestEvent event)
	{
		//touch();
		// isRequestSent = true;
		status = ProxySessionStatus.WAITING_CONNECT_RESPONSE;
		localHTTPChannel.getPipeline().remove("decoder");
		localHTTPChannel.getPipeline().remove("encoder");
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
		remoteAddr = host;
		if (host.indexOf(":") == -1)
		{
			if (event.method.equalsIgnoreCase(HttpMethod.CONNECT.getName()))
			{
				remoteAddr = remoteAddr + ":443";
			}
			else
			{
				remoteAddr = remoteAddr + ":80";
			}
		}
//		if (null == host)
//		{
//			SimpleSocketAddress addr = getRemoteAddress(event);
//			host = addr.toString();
//			event.setHeader("Host", host);
//		}
		readSequence.set(0);
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
				TCPChunkEvent chunk = new TCPChunkEvent();
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
		// restChunkList.clear();
		// seqChunkTable.clear();
		// waitingChunkSequence = 0;
		// writeFuture = null;
		// closeAfterFinish = false;
		sequence.set(0);
	}
	
	public void close()
	{
		if (ProxySessionManager.getInstance().getProxySession(getSessionID()) == null)
		{
			return;
		}
		if (null != localHTTPChannel && localHTTPChannel.isConnected())
		{
			localHTTPChannel.close();
		}
		localHTTPChannel = null;
		SocketConnectionEvent ev = new SocketConnectionEvent();
		ev.setHash(getSessionID());
		ev.status = SocketConnectionEvent.TCP_CONN_CLOSED;
		if (null != connection)
		{
			connection.send(ev);
		}
		status = ProxySessionStatus.SESSION_COMPLETED;
		ProxySessionManager.getInstance().removeSession(this);
		clearStatus();
	}
}
