/**
 * 
 */
package org.snova.c4.server.session;

import java.util.Map;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.util.NetworkHelper;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;

/**
 * @author wqy
 * 
 */
public class SessionManager
{
	protected Logger	          logger	     = LoggerFactory
	                                                   .getLogger(getClass());
	private static SessionManager instance = new SessionManager();
	private Map<Integer, Session>	sessionTable	= new ConcurrentHashMap<Integer, Session>();

	public static SessionManager getInstance()
	{
		return instance;
	}
	private SessionManager(){}
	
	private Session createSession(String sessionName, int id)
	{
		if(logger.isDebugEnabled())
		{
			logger.debug("Create session with name:" + sessionName);
		}
		DirectSession session = new DirectSession();
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
	
	public void clear()
	{
		for(Session session:sessionTable.values())
		{
			session.close();
		}
		sessionTable.clear();
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
					if(null != host)
					{
						if(host.indexOf(":") != -1)
						{
							host = host.substring(0, host.indexOf(":"));
						}
						if(NetworkHelper.isPrivateIP(host))
						{
							sessionName = "Direct";
						}
					}
					sessionName = "Direct";
					
					if(logger.isInfoEnabled())
					{
						logger.info("Handle URL:" +ev.method +" "+ ev.url + " by session:" + sessionName);
					}
					
					Session session = getSession(event.getHash());
					if (null == session)
					{
						session = createSession(sessionName, event.getHash());
						if(null == session)
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
					logger.error("NULL session for handling HTTP chunk event");
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
