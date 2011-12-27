/**
 * 
 */
package org.snova.heroku.client.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.arch.common.Pair;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.config.HerokuClientConfiguration.HerokuServerAuth;
import org.snova.heroku.client.connection.ProxyConnection;
import org.snova.heroku.client.connection.ProxyConnectionManager;
import org.snova.heroku.common.event.EventRestRequest;

/**
 * @author qiyingwang
 * 
 */
public class ProxySessionManager implements Runnable
{
	protected static Logger	           logger	     = LoggerFactory
	                                                         .getLogger(ProxySessionManager.class);
	private static ProxySessionManager	instance	 = new ProxySessionManager();
	
	private Map<Integer, ProxySession>	sessionTable	= new HashMap<Integer, ProxySession>();
	
	private ProxySessionManager()
	{
		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 1000,
		        5000, TimeUnit.MILLISECONDS);
	}
	
	public static ProxySessionManager getInstance()
	{
		return instance;
	}
	
	public void removeSession(ProxySession session)
	{
		sessionTable.remove(session.getSessionID());
		if (logger.isDebugEnabled())
		{
			logger.debug("Destroy Session:" + session.getSessionID());
		}
	}
	
	public ProxySession getProxySession(Integer sessionID)
	{
		synchronized (sessionTable)
		{
			return sessionTable.get(sessionID);
		}
	}
	
	private ProxySession createSession(Integer id, Channel ch)
	{
		ProxySession session = getProxySession(id);
		if (null == session)
		{
			session = new ProxySession(id, ch);
			synchronized (sessionTable)
			{
				sessionTable.put(id, session);
			}
		}
		if (logger.isDebugEnabled())
		{
			logger.debug("Create Session:" + id);
		}
		return session;
	}
	
	public ProxySession handleConnectionEvent(HTTPConnectionEvent event)
	{
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		// Channel localChannel = attach.first;
		Integer handleID = attach.second;
		ProxySession session = getProxySession(handleID);
		if (null != session)
		{
			if (event.status == HTTPConnectionEvent.CLOSED)
			{
				removeSession(session);
				session.close();
			}
		}
		else
		{
			logger.error("Can not find session with session ID:" + handleID);
		}
		return session;
	}
	
	public ProxySession handleRequest(HTTPRequestEvent event)
	{
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		Channel localChannel = attach.first;
		Integer handleID = attach.second;
		ProxySession session = getProxySession(handleID);
		if (null == session)
		{
			session = createSession(handleID, localChannel);
		}
		session.handle(event);
		return session;
	}
	
	public ProxySession handleChunk(HTTPChunkEvent event)
	{
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		// Channel localChannel = attach.first;
		Integer handleID = attach.second;
		ProxySession session = getProxySession(handleID);
		if (null != session)
		{
			session.handle(event);
		}
		return session;
	}
	
	@Override
	public void run()
	{
		if (sessionTable.size() > 0)
		{
			if(logger.isDebugEnabled())
			{
				logger.debug("Current session table has " + sessionTable.size());
			}
			List<HerokuServerAuth> auths = HerokuClientConfiguration
			        .getInstance().getHerokuServerAuths();
			for (HerokuServerAuth auth : auths)
			{
				ProxyConnection conn = ProxyConnectionManager.getInstance()
				        .getClientConnectionByAuth(auth);
				conn.send(new EventRestRequest());
			}
			
			for (ProxySession session:sessionTable.values())
            {
				session.routine();
            }
		}
	}
}
