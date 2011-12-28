/**
 * 
 */
package org.snova.heroku.server.handler;

import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.arch.event.Event;
import org.arch.event.EventConstants;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressEventV2;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.HerokuConstants;

/**
 * @author qiyingwang
 * 
 */
public class ServerEventHandler implements EventHandler
{
	protected Logger	               logger	     = LoggerFactory
	                                                         .getLogger(getClass());
	
	protected ScheduledExecutorService	pool	     = new ScheduledThreadPoolExecutor(
	                                                         50);
	protected LinkedList<Event>	       responseQueue	= new LinkedList<Event>();
	protected DirectFetchHandler	   fetchHandler	 = new DirectFetchHandler(
	                                                         this);
	//protected NettyFetchHandler	   fetchHandler	 = new NettyFetchHandler(
    //        this);
	public ScheduledExecutorService getThreadPool()
	{
		return pool;
	}
	
	private void handleRecvEvent(Event event)
	{
		TypeVersion tv = Event.getTypeVersion(event.getClass());
		if (null == tv)
		{
			logger.error("Failed to find registry type&version for class:"
			        + event.getClass().getName());
		}
		long ts = fetchHandler.touch();
		//if(ts - fetchHandler.getPingTime() > fetchHandler.getSelectWaitTime() * 5)
		//{
		//	logger.error("IO thread may be blocked!");
		//}
		int type = tv.type;
		switch (type)
		{
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				fetchHandler.fetch((HTTPChunkEvent) event);
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				fetchHandler.handleConnectionEvent((HTTPConnectionEvent) event);
				break;
			}
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				fetchHandler.fetch((HTTPRequestEvent) event);
				break;
			}
			case EventConstants.COMPRESS_EVENT_TYPE:
			{
				if(tv.version == 1)
				{
					((CompressEvent) event).ev.setAttachment(event.getAttachment());
					handleRecvEvent(((CompressEvent) event).ev);
				}
				else if(tv.version == 2)
				{
					((CompressEventV2) event).ev.setAttachment(event.getAttachment());
					handleRecvEvent(((CompressEventV2) event).ev);
				}
				break;
			}
			case EventConstants.ENCRYPT_EVENT_TYPE:
			{
				if(tv.version == 1)
				{
					((EncryptEvent) event).ev.setAttachment(event.getAttachment());
					handleRecvEvent(((EncryptEvent) event).ev);
				}
				else if(tv.version == 2)
				{
					((EncryptEventV2) event).ev.setAttachment(event.getAttachment());
					handleRecvEvent(((EncryptEventV2) event).ev);
				}
				break;
			}
			case HerokuConstants.EVENT_REST_REQEUST_TYPE:
			{
				break;
			}
			case HerokuConstants.EVENT_SOCKET_CONNECT_REQ_TYPE:
			{
				break;
			}
			
			default:
			{
				logger.error("Unsupported event type:" + type);
				break;
			}
		}
	}
	
	public int offer(Event ev, boolean encrypt)
	{
		//logger.info("Offer one event!");
		if(encrypt)
		{
			EncryptEventV2 enc = new EncryptEventV2(EncryptType.SE1, ev);
			enc.setHash(ev.getHash());
			ev = enc;
		}
		
		synchronized (responseQueue)
		{
			responseQueue.add(ev);
			System.out.println("Offer one event while current queue size:" + responseQueue.size());
			return responseQueue.size();
		}
	}
	
	void clearEventQueue()
	{
		synchronized (responseQueue)
		{
			responseQueue.clear();
		}
		
	}
	
	public LinkedList<Event> getEventQueue()
	{
		return responseQueue;
	}
	
	public int readyEventNum()
	{
		synchronized (responseQueue)
		{
			return responseQueue.size();
		}	
	}
	
	@Override
	public void onEvent(final EventHeader header, final Event event)
	{
		Object[] attach = (Object[]) event.getAttachment();
		//final EventSendService sendService = (EventSendService) attach[0];
		pool.submit(new Runnable()
		{
			@Override
			public void run()
			{
				try
                {
					handleRecvEvent(event);
                }
                catch (Exception e)
                {
	               e.printStackTrace();
                }
				
			}
		});
	}
	
}
