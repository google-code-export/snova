/**
 * 
 */
package org.snova.heroku.client.connection;

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
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.config.HerokuClientConfiguration.HerokuServerAuth;

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
	
	private ListSelector<HerokuServerAuth>	       seletor	      = null;
	
	public static ProxyConnectionManager getInstance()
	{
		return instance;
	}
	
	public boolean init(List<HerokuServerAuth> gaeAuths)
	{
		close();
		List<HerokuServerAuth> auths = new ArrayList<HerokuClientConfiguration.HerokuServerAuth>();
		auths.addAll(gaeAuths);
		
		for (HerokuServerAuth auth : HerokuClientConfiguration.getInstance()
		        .getHerokuServerAuths())
		{
			ProxyConnection conn = getClientConnectionByAuth(auth);
			if (null == conn)
			{
				logger.error("Failed to auth connetion for domain:" + auth.domain);
				auths.remove(auth);
			}
		}
		if (auths.isEmpty())
		{
			logger.error("Failed to connect remote Heroku server:" + gaeAuths);
			return false;
		}
		if (logger.isInfoEnabled())
		{
			int size = auths.size();
			//logger.info("Success to connect " + size + " GAE server"
			//        + (size > 1 ? "s" : ""));
			SharedObjectHelper.getTrace().info(
			        "Success to connect " + size + " GAE server"
			                + (size > 1 ? "s" : ""));
		}
		seletor = new ListSelector<HerokuServerAuth>(auths);
		return true;
	}
	
	public boolean init(HerokuServerAuth auth)
	{
		return init(Arrays.asList(auth));
	}
	
	public boolean init()
	{
		return init(HerokuClientConfiguration.getInstance().getHerokuServerAuths());
	}
	
	private boolean addProxyConnection(List<ProxyConnection> connlist,
	        ProxyConnection connection)
	{
		connlist.add(connection);
		return true;
	}
	
	public ProxyConnection getClientConnectionByAuth(HerokuServerAuth auth,
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
		if (connlist.size() >= HerokuClientConfiguration.getInstance()
		        .getConnectionPoolSize())
		{
			return connlist.get(0);
		}
		
		switch (HerokuClientConfiguration.getInstance().getConnectionModeType())
		{
			case HTTP:
			case HTTPS:
			{
				connection = new HTTPProxyConnection(auth);
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
	
	public ProxyConnection getClientConnectionByAuth(HerokuServerAuth auth)
	{
		return getClientConnectionByAuth(auth, true);
	}
	
	public ProxyConnection getClientConnection(HTTPRequestEvent event)
	{
		String appid = null != event ? HerokuClientConfiguration.getInstance()
		        .getBindingAppId(event.getHeader("Host")) : null;
		HerokuServerAuth auth = null;
		if (null == appid)
		{
			auth = seletor.select();
		}
		else
		{
			auth = HerokuClientConfiguration.getInstance().getHerokuServerAuth(appid);
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
