/**
 * 
 */
package org.snova.c4.client.connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.arch.event.http.HTTPRequestEvent;
import org.arch.util.ListSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.connection.v2.HTTPProxyConnectionV2;
import org.snova.framework.util.SharedObjectHelper;

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
	
	private ListSelector<C4ServerAuth>	       seletor	      = null;
	
	public static ProxyConnectionManager getInstance()
	{
		return instance;
	}
	
	public boolean init(List<C4ServerAuth> gaeAuths)
	{
		close();
		List<C4ServerAuth> auths = new ArrayList<C4ClientConfiguration.C4ServerAuth>();
		auths.addAll(gaeAuths);
		
		for (C4ServerAuth auth : C4ClientConfiguration.getInstance()
		        .getC4ServerAuths())
		{
			ProxyConnection conn = getClientConnectionByAuth(auth);
			if (null == conn)
			{
				logger.error("Failed to auth connetion for domain:"
				        + auth.domain);
				auths.remove(auth);
			}
		}
		if (auths.isEmpty())
		{
			logger.error("Failed to connect remote c4 server:" + gaeAuths);
			return false;
		}
		if (logger.isInfoEnabled())
		{
			int size = auths.size();
			// logger.info("Success to connect " + size + " GAE server"
			// + (size > 1 ? "s" : ""));
			SharedObjectHelper.getTrace().info(
			        "Success to found " + size + " c4 server"
			                + (size > 1 ? "s" : ""));
		}
		seletor = new ListSelector<C4ServerAuth>(auths);
		return true;
	}
	
	public boolean init(C4ServerAuth auth)
	{
		return init(Arrays.asList(auth));
	}
	
	public boolean init()
	{
		return init(C4ClientConfiguration.getInstance().getC4ServerAuths());
	}
	
	private boolean addProxyConnection(List<ProxyConnection> connlist,
	        ProxyConnection connection)
	{
		connlist.add(connection);
		return true;
	}
	
	public ProxyConnection getClientConnectionByAuth(C4ServerAuth auth,
	        boolean storeConnection)
	{
		ProxyConnection connection = null;
		List<ProxyConnection> connlist = conntionTable.get(auth.domain);
		if (null == connlist)
		{
			connlist = new ArrayList<ProxyConnection>();
			conntionTable.put(auth.domain, connlist);
		}
		for (ProxyConnection conn : connlist)
		{
			if (conn.isReady())
			{
				return conn;
			}
		}
		if (connlist.size() >= C4ClientConfiguration.getInstance()
		        .getConnectionPoolSize())
		{
			return connlist.get(0);
		}
		
		switch (C4ClientConfiguration.getInstance().getConnectionMode())
		{
			case HTTP:
			{
				if (!C4ClientConfiguration.getInstance().isServerPullEnable())
				{
					if (C4ClientConfiguration.getInstance()
					        .isDualConnectionEnable())
					{
						connection = new DualHTTPProxyConnection(auth);
					}
					else
					{
						connection = new HTTPProxyConnection(auth);
					}
				}
				else
				{
					connection = new HTTPProxyConnectionV2(auth);
				}
				addProxyConnection(connlist, connection);
				break;
			}
			default:
			{
				break;
			}
		}
		
		return connection;
	}
	
	public ProxyConnection getClientConnectionByAuth(C4ServerAuth auth)
	{
		return getClientConnectionByAuth(auth, true);
	}
	
	public ProxyConnection getClientConnection(HTTPRequestEvent event)
	{
		String domain = null != event ? C4ClientConfiguration.getInstance()
		        .getBindingDomain(event.getHeader("Host")) : null;
		C4ServerAuth auth = null;
		if (null == domain)
		{
			auth = seletor.select();
		}
		else
		{
			auth = C4ClientConfiguration.getInstance().getC4ServerAuth(domain);
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
