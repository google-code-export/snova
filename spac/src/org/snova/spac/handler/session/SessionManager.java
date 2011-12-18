/**
 * 
 */
package org.snova.spac.handler.session;

import java.util.Map;

import org.arch.common.Pair;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.arch.event.NamedEventHandler;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tykedog.csl.interpreter.CSL;

/**
 * @author wqy
 * 
 */
public class SessionManager
{
	protected Logger	          logger	     = LoggerFactory
	                                                     .getLogger(getClass());
	private CSL	                  scriptEngine;
	private Map<Integer, Session>	sessionTable	= new ConcurrentHashMap<Integer, Session>();
	
	public void setScriptEngine(CSL scriptEngine)
	{
		this.scriptEngine = scriptEngine;
	}
	
	private Session createSession(String sessionName, int id)
	{
		Session session = null;
		NamedEventHandler named = EventDispatcher.getSingletonInstance()
		        .getNamedEventHandler(sessionName);
		if (named != null)
		{
			session = new NamedEventHandlerSession(named);
		}
		else
		{
			if (sessionName.startsWith("socks"))
			{
				
			}
			else if (sessionName.equalsIgnoreCase("DIRECT"))
			{
				
			}
			else
			{
				session = new ForwardSession(sessionName);
			}
			
		}
		session.setName(sessionName);
		session.setID(id);
		return session;
	}
	
	private Session getSession(int handleID)
	{
		return sessionTable.get(handleID);
	}
	
	private void removeSession(Session session)
	{
		sessionTable.remove(session.getID());
	}
	
	public void handleEvent(EventHeader header, Event event)
	{
		Pair<Channel, Integer> attch = (Pair<Channel, Integer>) event
		        .getAttachment();
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
					String sessionName = (String) scriptEngine.invoke(
					        "SelectProxy", new Object[] { protocol, ev.method,
					                ev.url, ev });
					Session session = getSession(attch.second);
					if (null == session)
					{
						session = createSession(sessionName, attch.second);
					}
					else
					{
						if (!session.getName().equals(sessionName))
						{
							removeSession(session);
							session = createSession(sessionName, attch.second);
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
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				Session session = getSession(attch.second);
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
				Session session = getSession(attch.second);
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
