/**
 * 
 */
package org.snova.c4.client.connection;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventConstants;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressEventV2;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.handler.ProxySession;
import org.snova.c4.client.handler.ProxySessionManager;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.SocketConnectionEvent;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public abstract class ProxyConnection
{
	protected static Logger	                    logger	          = LoggerFactory
	                                                                      .getLogger(ProxyConnection.class);
	protected static C4ClientConfiguration	    cfg	              = C4ClientConfiguration
	                                                                      .getInstance();
	// private LinkedList<Event> queuedEvents = new LinkedList<Event>();
	protected static ClientSocketChannelFactory	clientChannelFactory;
	protected C4ServerAuth	                    auth	          = null;
	// private String authToken = null;
	// private AtomicInteger authTokenLock = new AtomicInteger(0);
	private EventHandler	                    outSessionHandler	= null;
	
	private long	                            lastsendtime	  = -1;
	private LinkedList<Event>	                queuedEvents	  = new LinkedList<Event>();
	
	protected ProxyConnectionStateListner	    stateListener;
	
	protected static ClientSocketChannelFactory getClientSocketChannelFactory()
	{
		if (null == clientChannelFactory)
		{
			if (null == SharedObjectHelper.getGlobalThreadPool())
			{
				ThreadPoolExecutor workerExecutor = new OrderedMemoryAwareThreadPoolExecutor(
				        20, 0, 0);
				SharedObjectHelper.setGlobalThreadPool(workerExecutor);
				
			}
			clientChannelFactory = new NioClientSocketChannelFactory(
			        SharedObjectHelper.getGlobalThreadPool(),
			        SharedObjectHelper.getGlobalThreadPool());
			
		}
		return clientChannelFactory;
	}
	
	public ProxyConnectionStateListner getStateListener()
	{
		return stateListener;
	}
	
	public void setStateListener(ProxyConnectionStateListner stateListener)
	{
		this.stateListener = stateListener;
	}
	
	protected void onAvailable()
	{
		if (null != stateListener)
		{
			stateListener.onAvailable();
		}
	}
	
	protected ProxyConnection(C4ServerAuth auth)
	{
		this.auth = auth;
	}
	
	public C4ServerAuth getC4ServerAuth()
	{
		return auth;
	}
	
	protected abstract boolean doSend(Buffer msgbuffer);
	
	protected abstract int getMaxDataPackageSize();
	
	protected void doClose()
	{
		
	}
	
	public abstract boolean isReady();
	
	protected void setAvailable(boolean flag)
	{
		// nothing
	}
	
	public void close()
	{
		doClose();
	}
	
	public boolean send(Event event, EventHandler handler)
	{
		outSessionHandler = handler;
		return send(event);
	}
	
	public boolean send(List<Event> events)
	{
		if (null != events)
		{
			for (Event event : events)
			{
				Event ready = null;
				if (event instanceof HTTPRequestEvent)
				{
					CompressEventV2 tmp = new CompressEventV2(
					        cfg.getCompressor(), event);
					tmp.setHash(event.getHash());
					event = tmp;
				}
				
				EncryptEventV2 enc = new EncryptEventV2(cfg.getEncrypter(),
				        event);
				enc.setHash(event.getHash());
				ready = enc;
				
				synchronized (queuedEvents)
				{
					queuedEvents.add(ready);
				}
				if (logger.isTraceEnabled())
				{
					logger.trace("Connection:" + this.hashCode()
					        + " queued with queue size:" + queuedEvents.size()
					        + ", session[" + event.getHash() + "] HTTP request");
					
				}
			}
		}
		
		long now = System.currentTimeMillis();
		if (!isReady()
		        && now - lastsendtime < C4ClientConfiguration.getInstance()
		                .getHTTPRequestTimeout())
		{
			if (logger.isTraceEnabled())
			{
				logger.trace("Connection:" + this.hashCode()
				        + " is not ready while ready event queue size:"
				        + queuedEvents.size());
			}
			// SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			// {
			// @Override
			// public void run()
			// {
			// send((List<Event>) null);
			// }
			// }, C4ClientConfiguration.getInstance().getMinWritePeriod() / 2,
			// TimeUnit.MILLISECONDS);
			return true;
		}
		// if (queuedEvents.isEmpty())
		// {
		// return true;
		// }
		setAvailable(false);
		
		Buffer msgbuffer = new Buffer(1024);
		synchronized (queuedEvents)
		{
			for (Event ev : queuedEvents)
			{
				ev.encode(msgbuffer);
				if (logger.isTraceEnabled())
				{
					logger.trace("Connection:" + this.hashCode()
					        + " send encode event for session[" + ev.getHash()
					        + "]");
				}
			}
			queuedEvents.clear();
		}
		
		lastsendtime = now;
		boolean ret = doSend(msgbuffer);
		return ret;
		
	}
	
	public boolean send(Event event)
	{
		if (null == event)
		{
			return send((List<Event>) null);
		}
		return send(Arrays.asList(event));
	}
	
	protected void handleRecvEvent(Event ev)
	{
		if (null == ev)
		{
			logger.error("NULL event to handle!");
			// close();
			return;
		}
		
		TypeVersion typever = Event.getTypeVersion(ev.getClass());
		
//		if (logger.isDebugEnabled())
//		{
//			logger.debug("Handle received session[" + ev.getHash()
//			        + "] response event:" + ev.getClass().getName());
//		}
		switch (typever.type)
		{
			case EventConstants.COMPRESS_EVENT_TYPE:
			{
				if (typever.version == 1)
				{
					handleRecvEvent(((CompressEvent) ev).ev);
				}
				else if (typever.version == 2)
				{
					handleRecvEvent(((CompressEventV2) ev).ev);
				}
				
				return;
			}
			case EventConstants.ENCRYPT_EVENT_TYPE:
			{
				if (typever.version == 1)
				{
					handleRecvEvent(((EncryptEvent) ev).ev);
				}
				else if (typever.version == 2)
				{
					handleRecvEvent(((EncryptEventV2) ev).ev);
				}
				return;
			}
			case C4Constants.EVENT_TCP_CHUNK_TYPE:
			case C4Constants.EVENT_TCP_CONNECTION_TYPE:
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			case HTTPEventContants.HTTP_RESPONSE_EVENT_TYPE:
			{
				break;
			}
			default:
			{
				logger.error("Unsupported event type:" + typever.type
				        + " for proxy connection");
				break;
			}
		}
		
		ProxySession session = ProxySessionManager.getInstance()
		        .getProxySession(ev.getHash());
		if (null != session)
		{
			session.handleResponse(ev);
			// session.close();
		}
		else
		{
			if (null != outSessionHandler)
			{
				EventHeader header = new EventHeader();
				header.type = Event.getTypeVersion(ev.getClass()).type;
				header.version = Event.getTypeVersion(ev.getClass()).version;
				header.hash = ev.getHash();
				// header.type = Event.getTypeVersion(ev.getClass())
				outSessionHandler.onEvent(header, ev);
			}
			else
			{
				if (typever.type != C4Constants.EVENT_TCP_CONNECTION_TYPE)
				{
					if (logger.isDebugEnabled())
					{
						logger.error("Failed o find session or handle to handle received session["
						        + ev.getHash()
						        + "] response event:"
						        + ev.getClass().getName());
					}
					
					SocketConnectionEvent tmp = new SocketConnectionEvent();
					tmp.setHash(ev.getHash());
					send(tmp);
				}
			}
		}
	}
	
	protected void doRecv(Buffer content)
	{
		Event ev = null;
		try
		{
			// int i = 0;
			while (content.readable())
			{
				ev = EventDispatcher.getSingletonInstance().parse(content);
				handleRecvEvent(ev);
				// i++;
			}
		}
		catch (Exception e)
		{
			logger.error(
			        "Failed to parse event while content rest:"
			                + content.readableBytes(), e);
			return;
		}
	}
}
