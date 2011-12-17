/**
 * 
 */
package org.snova.gae.client.connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.arch.event.http.HTTPRequestEvent;
import org.arch.util.ListSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.gae.client.config.GAEClientConfiguration;
import org.snova.gae.client.config.GAEClientConfiguration.GAEServerAuth;
import org.snova.gae.client.config.GAEClientConfiguration.XmppAccount;

/**
 * @author qiyingwang
 * 
 */
public class ProxyConnectionManager
{
	private static ProxyConnectionManager	   instance	      = new ProxyConnectionManager();
	protected Logger	                       logger	      = LoggerFactory
	                                                                  .getLogger(getClass());
	private Map<String, List<ProxyConnection>>	conntionTable	= new HashMap<String, List<ProxyConnection>>();
	
	private ListSelector<GAEServerAuth>	       seletor	      = null;
	
	public static ProxyConnectionManager getInstance()
	{
		return instance;
	}
	
	public boolean init(List<GAEServerAuth> gaeAuths)
	{
		close();
		List<GAEServerAuth> auths = new ArrayList<GAEClientConfiguration.GAEServerAuth>();
		auths.addAll(gaeAuths);
		for (GAEServerAuth auth : GAEClientConfiguration.getInstance()
		        .getGAEServerAuths())
		{
			ProxyConnection conn = getClientConnectionByAuth(auth);
			if (null == conn)
			{
				logger.error("Failed to auth connetion for appid:" + auth.appid);
				auths.remove(auth);
			}
		}
		if (auths.isEmpty())
		{
			logger.error("Failed to connect remote GAE server.");
			return false;
		}
		if (logger.isInfoEnabled())
		{
			int size = auths.size();
			logger.info("Success to connect " + size + " GAE server"
			        + (size > 1 ? "s" : ""));
			SharedObjectHelper.getTrace().info("Success to connect " + size + " GAE server"
			        + (size > 1 ? "s" : ""));
		}
		seletor = new ListSelector<GAEServerAuth>(auths);
		return true;
	}
	
	public boolean init(GAEServerAuth auth)
	{
		return init(Arrays.asList(auth));
	}
	
	public boolean init()
	{
		return init(GAEClientConfiguration.getInstance().getGAEServerAuths());
	}
	
	private boolean addProxyConnection(List<ProxyConnection> connlist,
	        ProxyConnection connection, boolean allowAdd)
	{
		if (!connlist.isEmpty())
		{
			connection.setAuthToken(connlist.get(0).getAuthToken());
		}
		else
		{
			if (!connection.auth())
			{
				return false;
			}
		}
		if (allowAdd)
		{
			connlist.add(connection);
		}
		return true;
	}
	
	public ProxyConnection getClientConnectionByAuth(GAEServerAuth auth,
	        boolean storeConnection)
	{
		ProxyConnection connection = null;
		List<ProxyConnection> connlist = conntionTable.get(auth.appid);
		if (null == connlist)
		{
			connlist = new ArrayList<ProxyConnection>();
			conntionTable.put(auth.appid, connlist);
		}
		for (ProxyConnection conn : connlist)
		{
			if (conn.isReady())
			{
				return conn;
			}
		}
		if (connlist.size() >= GAEClientConfiguration.getInstance()
		        .getConnectionPoolSize())
		{
			return connlist.get(0);
		}
		
		switch (GAEClientConfiguration.getInstance().getConnectionModeType())
		{
			case HTTP:
			case HTTPS:
			{
				connection = new HTTPProxyConnection(auth);
				if (!addProxyConnection(connlist, connection, storeConnection))
				{
					return null;
				}
				break;
			}
			case XMPP:
			{
				for (XmppAccount account : GAEClientConfiguration.getInstance()
				        .getXmppAccounts())
				{
					try
					{
						connection = new XMPPProxyConnection(auth, account);
						if (!addProxyConnection(connlist, connection,
						        storeConnection))
						{
							return null;
						}
					}
					catch (Exception e)
					{
						logger.error(
						        "Failed to create XMPP proxy connection for jid:"
						                + account.jid, e);
					}
				}
				break;
			}
			default:
			{
				break;
			}
		}
		
		return connection;
	}
	
	public ProxyConnection getClientConnectionByAuth(GAEServerAuth auth)
	{
		return getClientConnectionByAuth(auth, true);
	}
	
	public ProxyConnection getClientConnection(HTTPRequestEvent event)
	{
		String appid = null != event ? GAEClientConfiguration.getInstance()
		        .getBindingAppId(event.getHeader("Host")) : null;
		GAEServerAuth auth = null;
		if (null == appid)
		{
			auth = seletor.select();
		}
		else
		{
			auth = GAEClientConfiguration.getInstance().getGAEServerAuth(appid);
		}
		return getClientConnectionByAuth(auth);
	}
	
	public void close()
	{
		for (List<ProxyConnection> connlist : conntionTable.values())
		{
			for (ProxyConnection conn : connlist)
			{
				conn.close();
			}
		}
		conntionTable.clear();
	}
	
}
