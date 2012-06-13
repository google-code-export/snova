/**
 * 
 */
package org.snova.c4.server.service;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.arch.event.misc.EncryptType;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.EventRestRequest;
import org.snova.c4.server.session.Session;
import org.snova.c4.server.session.SessionManager;

/**
 * @author wqy
 * 
 */
public class EventService
{
	// private static EventService instance = new EventService();
	private static Map<String, EventService>	instanceTable	= new HashMap<String, EventService>();
	
	public static EventService getInstance(String userToken)
	{
		synchronized (instanceTable)
		{
			if (!instanceTable.containsKey(userToken))
			{
				instanceTable.put(userToken, new EventService(userToken));
			}
			return instanceTable.get(userToken);
		}
	}
	
	private static final int	          MAX_EVENT_QUEUE_SIZE	         = 1000;
	protected Logger	                  logger	                     = LoggerFactory
	                                                                             .getLogger(getClass());
	// protected LinkedList<Event> reponseEventQueue = new LinkedList<Event>();
	
	protected Map<Integer, AtomicInteger>	sessionReponseQueueSizeTable	= new ConcurrentHashMap<Integer, AtomicInteger>();
	protected LinkedList<Event>	          responseQueue	                 = new LinkedList<Event>();
	private Map<Integer, Channel>	      suspendChannels	             = new ConcurrentHashMap<Integer, Channel>();
	
	private String	                      userToken;
	
	private EventService(String userToken)
	{
		C4Events.init(null, true);
		this.userToken = userToken;
	}
	
	public int getRestEventQueueSize()
	{
		synchronized (responseQueue)
		{
			return responseQueue.size();
		}
	}
	
	public void releaseEvents()
	{
		if (!responseQueue.isEmpty())
		{
			logger.info("Releaase all queued events");
			responseQueue.clear();
			sessionReponseQueueSizeTable.clear();
		}
		instanceTable.remove(userToken);
		suspendChannels.clear();
	}
	
	public void offer(Event ev, Channel ch)
	{
		synchronized (responseQueue)
		{
			EncryptEventV2 enc = new EncryptEventV2(EncryptType.SE1, ev);
			enc.setHash(ev.getHash());
			responseQueue.add(enc);
			int sessionQueueSize = sessionEventQueueIncrementAndGet(ev
			        .getHash());
			if (sessionQueueSize > MAX_EVENT_QUEUE_SIZE && null != ch)
			{
				ch.setReadable(false);
				suspendChannels.put(ev.getHash(), ch);
			}
			if(logger.isDebugEnabled())
			{
				logger.debug("Session[" + ev.getHash()
				        + "] offer one event to queue while size="
				        + sessionQueueSize);
			}

		}
		synchronized (this)
		{
			this.notify();
		}
		RSocketService.eventNotify(userToken);
	}
	
	
	public void removeSessionQueue(int sessionID)
	{
		sessionReponseQueueSizeTable.remove(sessionID);
		suspendChannels.remove(sessionID);
	}
	
	public int extractEventResponses(Buffer buf)
	{
		return extractEventResponses(buf, 512 * 1024);
	}
	
	private int sessionEventQueueDecrementAndGet(int sessionID)
	{
		AtomicInteger sessionQueueSize = sessionReponseQueueSizeTable
		        .get(sessionID);
		if (null == sessionQueueSize)
		{
			return 0;
		}
		return sessionQueueSize.decrementAndGet();
	}
	
	private int sessionEventQueueIncrementAndGet(int sessionID)
	{
		AtomicInteger sessionQueueSize = sessionReponseQueueSizeTable
		        .get(sessionID);
		if (null == sessionQueueSize)
		{
			sessionQueueSize = new AtomicInteger(0);
			sessionReponseQueueSizeTable.put(sessionID, sessionQueueSize);
		}
		return sessionQueueSize.incrementAndGet();
	}
	
	public int extractEventResponses(Buffer buf, int maxSize)
	{
		int count = 0;
		synchronized (responseQueue)
		{
			LinkedList<Event> queue = responseQueue;
			Event ev = null;
			while (!queue.isEmpty())
			{
				ev = queue.removeFirst();
				ev.encode(buf);
				int sessionQueueSize = sessionEventQueueDecrementAndGet(ev
				        .getHash());
				if (suspendChannels.containsKey(ev.getHash())
				        && sessionQueueSize < MAX_EVENT_QUEUE_SIZE)
				{
					suspendChannels.remove(ev.getHash()).setReadable(true);
				}
				count++;
				if (buf.readableBytes() >= maxSize)
				{
					break;
				}
			}
		}
		SessionManager.getInstance(userToken).getEventRestNotify().encode(buf);
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
		int type = tv.type;
		EventHeader header = new EventHeader();
		header.hash = event.getHash();
		header.type = tv.type;
		header.version = tv.version;
		switch (type)
		{
			case C4Constants.EVENT_SEQUNCEIAL_CHUNK_TYPE:
			{
				SessionManager.getInstance(userToken)
				        .handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				SessionManager.getInstance(userToken)
				        .handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				SessionManager.getInstance(userToken)
				        .handleEvent(header, event);
				break;
			}
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				// fetchHandler.fetch((HTTPRequestEvent) event);
				SessionManager.getInstance(userToken)
				        .handleEvent(header, event);
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
				for (Integer sessionId : req.restSessions)
				{
					Session s = SessionManager.getInstance(userToken)
					        .getSession(sessionId);
					if (null == s)
					{
						HTTPConnectionEvent closeEvent = new HTTPConnectionEvent(
						        HTTPConnectionEvent.CLOSED);
						closeEvent.setHash(sessionId);
						offer(closeEvent, null);
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
	
	public void dispatchEvent(final Buffer content) throws Exception
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
