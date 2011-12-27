/**
 * 
 */
package org.snova.heroku.client.connection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.arch.buffer.Buffer;
import org.arch.common.Pair;
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
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.config.HerokuClientConfiguration.HerokuServerAuth;
import org.snova.heroku.client.handler.ProxySession;
import org.snova.heroku.client.handler.ProxySessionManager;
import org.snova.heroku.common.HerokuConstants;
import org.snova.heroku.common.event.EventRestNotify;
import org.snova.heroku.common.event.EventRestRequest;

/**
 * @author qiyingwang
 * 
 */
public abstract class ProxyConnection
{
	protected Logger	                    logger	          = LoggerFactory
	                                                                  .getLogger(getClass());
	protected static HerokuClientConfiguration	cfg	              = HerokuClientConfiguration
	                                                                  .getInstance();
	//private LinkedList<Event>	            queuedEvents	  = new LinkedList<Event>();
	protected HerokuServerAuth	                auth	          = null;
	//private String	                        authToken	      = null;
	//private AtomicInteger	                        authTokenLock	  = new AtomicInteger(0);
	private EventHandler	                outSessionHandler	= null;
	
	private long lastsendtime = -1;
	private LinkedList<Event>	            queuedEvents	  = new LinkedList<Event>();
	
	protected ProxyConnection(HerokuServerAuth auth)
	{
		this.auth = auth;
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
	
	public boolean send(Event event)
	{
		
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		
		
		if (null == attach)
		{
			attach = new Pair<Channel, Integer>(null, -1);
		}
		
		event.setHash(attach.second);
		//CompressorType compressType = cfg.getCompressorType();
		//CompressEvent comress = new CompressEvent(compressType, event);
		//comress.setHash(attach.second);
		EncryptEventV2 enc = new EncryptEventV2(cfg.getEncrypterType(), event);
		enc.setHash(attach.second);
		synchronized (queuedEvents)
        {
			queuedEvents.add(enc);
        }
		if (logger.isDebugEnabled())
		{
			if (event instanceof HTTPRequestEvent)
			{
				logger.debug("Connection:" + this.hashCode()
				        + " send out with queue size:" + queuedEvents.size() + ", session[" + attach.second
				        + "] HTTP request:");
				logger.debug(event.toString());
			}
		}
		long now = System.currentTimeMillis();
		if(queuedEvents.size() < 10 && now - lastsendtime < 1000 && !isReady())
		{
			return true;
		}
		setAvailable(false);
		Buffer msgbuffer =  new Buffer(1024);
		synchronized (queuedEvents)
        {
			for(Event ev:queuedEvents)
			{
				ev.encode(msgbuffer);
			}
			queuedEvents.clear();
        }
		lastsendtime = now;
		return doSend(msgbuffer);
	}
	
	private void handleRecvEvent(Event ev)
	{
		if (null == ev)
		{
			close();
			return;
		}

		TypeVersion typever = Event.getTypeVersion(ev.getClass());

		if (logger.isDebugEnabled())
		{
			logger.debug("Handle received session[" + ev.getHash()
			        + "] response event[" + typever.type + "]");
		}
		switch (typever.type)
		{
			case EventConstants.COMPRESS_EVENT_TYPE:
			{
				if(typever.version == 1)
				{
					handleRecvEvent(((CompressEvent) ev).ev);
				}
				else if(typever.version == 2)
				{
					handleRecvEvent(((CompressEventV2) ev).ev);
				}
				
				return;
			}
			case EventConstants.ENCRYPT_EVENT_TYPE:
			{
				if(typever.version == 1)
				{
					handleRecvEvent(((EncryptEvent) ev).ev);
				}
				else if(typever.version == 2)
				{
					handleRecvEvent(((EncryptEventV2) ev).ev);
				}
				return;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			case HTTPEventContants.HTTP_RESPONSE_EVENT_TYPE:
			{
				// just let
				if (logger.isDebugEnabled())
				{
					logger.debug("Proxy connection received HTTP response:");
					logger.debug(ev.toString());
				}
				break;
			}
			case HerokuConstants.EVENT_REST_NOTIFY_TYPE:
			{
				EventRestNotify notify = (EventRestNotify) ev;
				if(notify.rest > 0)
				{
					send(new EventRestRequest());
					logger.info("Heroku server has " + notify.rest + " responses!");
				}
				return;
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
				logger.error("Failed o find session or handle to handle received session["
				        + ev.getHash() + "] response event.");
			}
		}
	}
	
	protected void doRecv(Buffer content)
	{
		Event ev = null;
		try
		{
			//int i = 0;
			while(content.readable())
			{
				ev = EventDispatcher.getSingletonInstance().parse(content);
				handleRecvEvent(ev);
				//i++;
			}
		}
		catch (Exception e)
		{
			logger.error("Failed to parse event.", e);
			return;
		}
	}
}
