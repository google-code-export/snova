/**
 * 
 */
package org.snova.c4.server.session;

import java.util.HashMap;
import java.util.Map;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.EventRestNotify;
import org.snova.c4.server.service.EventService;

/**
 * @author wqy
 * 
 */
public class SessionManager
{
	protected Logger	                       logger	      = LoggerFactory
	                                                                  .getLogger(getClass());
	private static Map<String, SessionManager>	instanceTable	= new HashMap<String, SessionManager>();
	// private static SessionManager instance = new SessionManager();
	private Map<Integer, Session>	           sessionTable	  = new ConcurrentHashMap<Integer, Session>();
	
	public static SessionManager getInstance(String userToken)
	{
		synchronized (instanceTable)
		{
			if (!instanceTable.containsKey(userToken))
			{
				instanceTable.put(userToken, new SessionManager(userToken));
			}
			return instanceTable.get(userToken);
		}
	}
	
	private String	userToken;
	
	private SessionManager(String userToken)
	{
		this.userToken = userToken;
	}
	
	public String getUserToken()
	{
		return userToken;
	}
	
	private Session createSession(String sessionName, int id)
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Create session with name:" + sessionName);
		}
		DirectSession session = new DirectSession(this);
		session.setName(sessionName);
		session.setID(id);
		sessionTable.put(id, session);
		return session;
	}
	
	public Session getSession(int handleID)
	{
		return sessionTable.get(handleID);
	}
	
	public void removeSession(Session session)
	{
		sessionTable.remove(session.getID());
	}
	
	public EventRestNotify getEventRestNotify()
	{
		EventRestNotify ev = new EventRestNotify();
		ev.rest = EventService.getInstance(userToken).getRestEventQueueSize();
		for (Session session : sessionTable.values())
		{
			ev.restSessions.add(session.getID());
		}
		return ev;
	}
	
	public void clear()
	{
		for (Session session : sessionTable.values())
		{
			session.close();
		}
		sessionTable.clear();
		instanceTable.remove(userToken);
	}
	
	public void handleEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				try
				{
					HTTPRequestEvent ev = (HTTPRequestEvent) event;
					String protocol = "http";
					if (ev.method.equalsIgnoreCase("Connect"))
					{
						protocol = "https";
					}
					if (ev.url.startsWith("https"))
					{
						protocol = "https";
					}
					String sessionName = null;
					String host = ev.getHeader("Host");
					if (null != host)
					{
						if (host.indexOf(":") != -1)
						{
							host = host.substring(0, host.indexOf(":"));
						}
					}
					sessionName = "Direct";
					
					if (logger.isInfoEnabled())
					{
						logger.info("Handle URL:" + ev.method + " " + ev.url
						        + " by session:" + sessionName);
					}
					
					Session session = getSession(event.getHash());
					if (null == session)
					{
						session = createSession(sessionName, event.getHash());
						if (null == session)
						{
							return;
						}
					}
					session.handleEvent(header, event);
				}
				catch (Exception e)
				{
					logger.error("Failed to handler event:", e);
				}
				break;
			}
			case C4Constants.EVENT_SEQUNCEIAL_CHUNK_TYPE:
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				Session session = getSession(event.getHash());
				if (null != session)
				{
					session.handleEvent(header, event);
				}
				else
				{
					logger.error("NULL session[" + event.getHash()
					        + "] found to handle HTTP chunk event");
				}
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				HTTPConnectionEvent ev = (HTTPConnectionEvent) event;
				Session session = getSession(event.getHash());
				if (null != session)
				{
					session.handleEvent(header, event);
					if (ev.status == HTTPConnectionEvent.CLOSED)
					{
						removeSession(session);
						EventService.getInstance(userToken).removeSessionQueue(event.getHash());
					}
				}
				break;
			}
			default:
			{
				logger.error("Unexpected event type:" + header.type);
				break;
			}
		}
	}
}
