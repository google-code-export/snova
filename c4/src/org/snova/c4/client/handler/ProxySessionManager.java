/**
 * 
 */
package org.snova.c4.client.handler;

import java.util.HashMap;
import java.util.LinkedList;
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
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.ConnectionMode;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.connection.ProxyConnection;
import org.snova.c4.client.connection.ProxyConnectionManager;
import org.snova.c4.common.event.EventRestRequest;
import org.snova.framework.util.SharedObjectHelper;

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
		if (C4ClientConfiguration.getInstance().getConnectionModeType()
		        .equals(ConnectionMode.HTTP))
		{
			SharedObjectHelper.getGlobalTimer().schedule(this,
			        C4ClientConfiguration.getInstance().getHeartBeatPeriod(),
			        TimeUnit.MILLISECONDS);
		}
		
		// new Thread(this).start();
	}
	
	public static ProxySessionManager getInstance()
	{
		return instance;
	}
	
	public synchronized void removeSession(ProxySession session)
	{
		if (null != sessionTable.remove(session.getSessionID()))
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Destroy Session:" + session.getSessionID());
			}
		}
		
	}
	
	public synchronized ProxySession getProxySession(Integer sessionID)
	{
		return sessionTable.get(sessionID);
	}
	
	private synchronized ProxySession createSession(Integer id, Channel ch)
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
		// handleID = attach.first.getId();
		ProxySession session = getProxySession(handleID);
		if (null != session)
		{
			if (event.status == HTTPConnectionEvent.CLOSED)
			{
				// removeSession(session);
				session.close();
			}
		}
		else
		{
			//logger.error("Can not find session with session ID:" + handleID);
		}
		return session;
	}
	
	public ProxySession handleRequest(HTTPRequestEvent event)
	{
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		Channel localChannel = attach.first;
		Integer handleID = attach.second;
		// handleID = attach.first.getId();
		ProxySession session = getProxySession(handleID);
		if (null == session)
		{
			session = createSession(handleID, localChannel);
		}
		event.setHash(handleID);
		session.handle(event);
		return session;
	}
	
	public ProxySession handleChunk(HTTPChunkEvent event)
	{
		Pair<Channel, Integer> attach = (Pair<Channel, Integer>) event
		        .getAttachment();
		// Channel localChannel = attach.first;
		Integer handleID = attach.second;
		// handleID = attach.first.getId();
		ProxySession session = getProxySession(handleID);
		if (null != session)
		{
			session.handle(event);
		}
		return session;
	}
	
	private void sendHeartBeat(List<ProxySession> sessions)
	{
		if (C4ClientConfiguration.getInstance().getConnectionModeType()
		        .equals(ConnectionMode.HTTP))
		{
			List<C4ServerAuth> auths = C4ClientConfiguration.getInstance()
			        .getC4ServerAuths();
			for (C4ServerAuth auth : auths)
			{
				ProxyConnection conn = ProxyConnectionManager.getInstance()
				        .getClientConnectionByAuth(auth);
				EventRestRequest ev = new EventRestRequest();
				for (ProxySession s : sessions)
				{
					if(s.getProxyConnection() == conn)
					{
						ev.restSessions.add(s.getSessionID());
					}
				}
				conn.send(new EventRestRequest());
			}
			
		}
	}
	
	@Override
	public void run()
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Current session table has " + sessionTable.size());
		}
		try
		{
			List<ProxySession> notCompleteSession = new LinkedList<ProxySession>();
			synchronized (this)
			{
				List<ProxySession> clonesList = new LinkedList<ProxySession>(sessionTable.values());
				for (ProxySession session : clonesList)
				{
					ProxySessionStatus status = session.routine();
					if (status
					        .equals(ProxySessionStatus.WAITING_CONNECT_RESPONSE)
					        || status
					                .equals(ProxySessionStatus.WAITING_RESPONSE))
					{
						notCompleteSession.add(session);
					}
				}
			}
			if (!sessionTable.isEmpty())
			{
				sendHeartBeat(notCompleteSession);
			}
			
			if (notCompleteSession.isEmpty())
			{
				SharedObjectHelper.getGlobalTimer().schedule(
				        this,
				        C4ClientConfiguration.getInstance()
				                .getHeartBeatPeriod(), TimeUnit.MILLISECONDS);
			}
			else
			{
				int time = C4ClientConfiguration.getInstance()
				        .getHeartBeatPeriod() / notCompleteSession.size();
				if (time < 500)
				{
					time = 500;
				}
				SharedObjectHelper.getGlobalTimer().schedule(this, time,
				        TimeUnit.MILLISECONDS);
			}
		}
		catch (Throwable e)
		{
			logger.error("Failed routine.", e);
		}
	}
}
