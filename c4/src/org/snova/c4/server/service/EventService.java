/**
 * 
 */
package org.snova.c4.server.service;

import java.util.LinkedList;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventConstants;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressEventV2;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.EventRestRequest;
import org.snova.c4.common.event.SequentialChunkEvent;
import org.snova.c4.server.session.Session;
import org.snova.c4.server.session.SessionManager;

/**
 * @author wqy
 * 
 */
public class EventService
{
	private static EventService	instance	= new EventService();
	
	public static EventService getInstance()
	{
		return instance;
	}
	
	protected Logger	        logger	          = LoggerFactory
	                                                      .getLogger(getClass());
	protected LinkedList<Event>	reponseEventQueue	= new LinkedList<Event>();
	
	private EventService()
	{
		C4Events.init(null, true);
	}
	
	public int getRestEventQueueSize()
	{
		return reponseEventQueue.size();
	}
	
	public void releaseEvents()
	{
		if(!reponseEventQueue.isEmpty())
		{
			logger.info("Releaase all queued events");
			reponseEventQueue.clear();
		}
	}
	
	public void offer(Event ev)
	{
		synchronized (reponseEventQueue)
		{
			reponseEventQueue.add(ev);
			while (reponseEventQueue.size() > 200)
			{
				try
				{
					reponseEventQueue.wait(500);
				}
				catch (InterruptedException e)
				{
					
				}
			}
		}
	}
	
	public int extractEventResponses(Buffer buf)
	{
		int count = 0;
		synchronized (reponseEventQueue)
		{
			do
			{
				if (buf.readableBytes() >= 1024 * 1024)
				{
					break;
				}
				Event ev = null;
				if (reponseEventQueue.isEmpty())
				{
					break;
				}
				ev = reponseEventQueue.removeFirst();
				ev.encode(buf);
				count++;
			} while (true);
			reponseEventQueue.notifyAll();
		}
		return count;
	}
	
	private void handleEvent(Event event)
	{
		TypeVersion tv = Event.getTypeVersion(event.getClass());
		if (null == tv)
		{
			logger.error("Failed to find registry type&version for class:"
			        + event.getClass().getName());
		}
		// if(ts - fetchHandler.getPingTime() > fetchHandler.getSelectWaitTime()
		// * 5)
		// {
		// logger.error("IO thread may be blocked!");
		// }
		int type = tv.type;
		EventHeader header = new EventHeader();
		header.hash = event.getHash();
		header.type = tv.type;
		header.version = tv.version;
		switch (type)
		{
			case C4Constants.EVENT_SEQUNCEIAL_CHUNK_TYPE:
			{
				SequentialChunkEvent sequnce = (SequentialChunkEvent) event;
				SessionManager.getInstance().handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				SessionManager.getInstance().handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				SessionManager.getInstance().handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				// fetchHandler.fetch((HTTPRequestEvent) event);
				SessionManager.getInstance().handleEvent(header, event);
				break;
			}
			case EventConstants.COMPRESS_EVENT_TYPE:
			{
				if (tv.version == 1)
				{
					((CompressEvent) event).ev.setAttachment(event
					        .getAttachment());
					handleEvent(((CompressEvent) event).ev);
				}
				else if (tv.version == 2)
				{
					((CompressEventV2) event).ev.setAttachment(event
					        .getAttachment());
					handleEvent(((CompressEventV2) event).ev);
				}
				break;
			}
			case EventConstants.ENCRYPT_EVENT_TYPE:
			{
				if (tv.version == 1)
				{
					((EncryptEvent) event).ev.setAttachment(event
					        .getAttachment());
					handleEvent(((EncryptEvent) event).ev);
				}
				else if (tv.version == 2)
				{
					((EncryptEventV2) event).ev.setAttachment(event
					        .getAttachment());
					handleEvent(((EncryptEventV2) event).ev);
				}
				break;
			}
			case C4Constants.EVENT_REST_REQEUST_TYPE:
			{
				EventRestRequest req = (EventRestRequest) event;
				for(Integer sessionId:req.restSessions)
				{
					Session s = SessionManager.getInstance().getSession(sessionId);
					if(null == s)
					{
						HTTPConnectionEvent closeEvent = new HTTPConnectionEvent(HTTPConnectionEvent.CLOSED);
						closeEvent.setHash(sessionId);
						offer(closeEvent);
					}
					else
					{
						s.routine();
					}
				}
				break;
			}
			case C4Constants.EVENT_SOCKET_CONNECT_REQ_TYPE:
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
	
	public void dispatchEvent(Buffer content) throws Exception
	{
		while (content.readable())
		{
			Event event = EventDispatcher.getSingletonInstance().parse(content);
			// event.setAttachment(new Object[] { sendService });
			handleEvent(event);
			// EventDispatcher.getSingletonInstance().dispatch(event);
		}
	}
}
