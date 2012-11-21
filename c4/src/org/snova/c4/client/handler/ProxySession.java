/**
 * 
 */
package org.snova.c4.client.handler;

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
	protected static Logger logger = LoggerFactory
	        .getLogger(ProxySession.class);
	private ProxyConnectionManager connectionManager = ProxyConnectionManager
	        .getInstance();
	private ProxyConnection pushConnection = null;
	private ProxyConnection pullConnection = null;
	private Integer sessionID;
	private String remoteAddr;
	private Channel localHTTPChannel;
	private ChannelFuture writeFuture;

	private AtomicInteger sequence = new AtomicInteger(0);

	private AtomicInteger readSequence = new AtomicInteger(0);

	public ProxySession(Integer id, Channel localChannel)
	{
		this.sessionID = id;
		this.localHTTPChannel = localChannel;
	}

	public Integer getSessionID()
	{
		return sessionID;
	}

	private void initConnection(HTTPRequestEvent event)
	{
		if (null == pushConnection)
		{
			ProxyConnection[] conns = connectionManager
			        .getDualClientConnection(event);
			pushConnection = conns[0];
			pullConnection = conns[1];
			pushConnection.setSession(this);
			pullConnection.setSession(this);
			pushConnection.setPullConnection(false);
			pullConnection.setPullConnection(true);
			pushConnection.start();
			pullConnection.start();
			pullConnection.pullData();
		}
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
		if (res instanceof SocketConnectionEvent)
		{
			SocketConnectionEvent conn = (SocketConnectionEvent) res;
			if (conn.status == SocketConnectionEvent.TCP_CONN_CLOSED)
			{
				if (conn.addr.equalsIgnoreCase(remoteAddr))
				{
					if (null != writeFuture)
					{
						writeFuture.addListener(new ChannelFutureListener()
						{
							@Override
							public void operationComplete(ChannelFuture future)
							        throws Exception
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
				//chunks.put(chunk.sequence, chunk);
				//writeChunk();
				localHTTPChannel.write(ChannelBuffers.wrappedBuffer(chunk.content));
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
		localHTTPChannel.getPipeline().remove("decoder");
		localHTTPChannel.getPipeline().remove("encoder");
		initConnection(event);
		pushConnection.send(event);
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
		readSequence.set(0);

		if (event.method.equalsIgnoreCase(HttpMethod.CONNECT.getName()))
		{
			handleConnect(event);
		}
		else
		{
			if (event.url.startsWith("http://" + host))
			{
				int start = "http://".length();
				int end = event.url.indexOf("/", start);
				event.url = event.url.substring(end);
			}
			initConnection(event);
			pushConnection.send(event);

			if (logger.isDebugEnabled())
			{
				logger.debug("Session[" + getSessionID() + "] sent request.");
				logger.debug(event.toString());
			}
		}
	}

	public synchronized void handle(HTTPChunkEvent event)
	{
		if (null != pushConnection)
		{
			if (event.content.length > 0)
			{
				TCPChunkEvent chunk = new TCPChunkEvent();
				chunk.setHash(getSessionID());
				chunk.content = event.content;
				chunk.sequence = sequence.getAndIncrement();
				pushConnection.send(chunk);
			}
		}
		else
		{
			close();
		}
	}

	private void clearStatus()
	{
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
		ev.addr = remoteAddr;
		ev.status = SocketConnectionEvent.TCP_CONN_CLOSED;
		if (null != pushConnection)
		{
			pushConnection.send(ev);
			pushConnection.stop();
		}
		if (null != pullConnection)
		{
			pullConnection.stop();
		}
		ProxySessionManager.getInstance().removeSession(this);
		clearStatus();

		connectionManager.recycleProxyConnection(pushConnection);
		connectionManager.recycleProxyConnection(pullConnection);
		pushConnection = null;
		pullConnection = null;
	}
}
