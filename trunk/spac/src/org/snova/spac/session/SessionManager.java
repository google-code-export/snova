/**
 * 
 */
package org.snova.spac.session;

import java.net.UnknownHostException;
import java.util.Map;

import org.arch.common.Pair;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.arch.event.NamedEventHandler;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
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
		if(logger.isDebugEnabled())
		{
			logger.debug("Create session with name:" + sessionName);
		}
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
				try
                {
	                session = new SocksSession(sessionName);
                }
                catch (UnknownHostException e)
                {
	                logger.error("Failed to create socks session", e);
	                return null;
                }
			}
			else if (sessionName.equalsIgnoreCase("DIRECT"))
			{
				session = new DirectSession();
			}
			else if (sessionName.toLowerCase().startsWith("hosts"))
			{
				session = new HostsFowwardSession(sessionName);
			}
			else if (sessionName.toLowerCase().startsWith("google"))
			{
				session = new GoogleForwardSession(sessionName);
			}
			else
			{
				session = new ForwardSession(sessionName);
			}
			
		}
		session.setName(sessionName);
		session.setID(id);
		sessionTable.put(id, session);
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
					if(null == sessionName)
					{
						sessionName = (String) scriptEngine.invoke(
						        "SelectProxy", new Object[] { protocol, ev.method,
						                ev.url, ev });
					}
					
					if(logger.isInfoEnabled())
					{
						logger.info("Handle URL:" +ev.method +" "+ ev.url + " by session:" + sessionName);
					}
					if(null == sessionName)
					{
						HttpResponse res = new DefaultHttpResponse(
						        HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
						res.setContent(ChannelBuffers.wrappedBuffer("No spac session found for this url.".getBytes()));
						attch.first.write(res);
					}
					Session session = getSession(attch.second);
					if (null == session)
					{
						session = createSession(sessionName, attch.second);
						if(null == session)
						{
							return;
						}
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
