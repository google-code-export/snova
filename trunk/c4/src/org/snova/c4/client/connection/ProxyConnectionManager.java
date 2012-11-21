/**
 * 
 */
package org.snova.c4.client.connection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import org.arch.event.http.HTTPRequestEvent;
import org.arch.util.ListSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.config.C4ClientConfiguration.ConnectionMode;
import org.snova.c4.client.connection.util.ConnectionHelper;
import org.snova.c4.common.event.UserLoginEvent;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public class ProxyConnectionManager
{
	private static ProxyConnectionManager instance = new ProxyConnectionManager();
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private Map<String, LinkedBlockingDeque<ProxyConnection>> freeConnTable = new HashMap<String, LinkedBlockingDeque<ProxyConnection>>();

	private ListSelector<C4ServerAuth> seletor = null;

	public static ProxyConnectionManager getInstance()
	{
		return instance;
	}

	public boolean init(List<C4ServerAuth> gaeAuths)
	{
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
			else
			{
				UserLoginEvent ev = new UserLoginEvent();
				ev.user = ConnectionHelper.getUserToken();
				conn.send(ev);
				recycleProxyConnection(conn);
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

	public boolean recycleProxyConnection(ProxyConnection connection)
	{
		if (null == connection)
		{
			return false;
		}
		LinkedBlockingDeque<ProxyConnection> list = freeConnTable
		        .get(connection.getC4ServerAuth().domain);
		if (list.size() >= 30)
		{
			connection.close();
			return false;
		}
		list.add(connection);
		return true;
	}

	public ProxyConnection getClientConnectionByAuth(C4ServerAuth auth)
	{
		ProxyConnection connection = null;
		LinkedBlockingDeque<ProxyConnection> connlist = freeConnTable
		        .get(auth.domain);
		if (null == connlist)
		{
			connlist = new LinkedBlockingDeque<ProxyConnection>();
			freeConnTable.put(auth.domain, connlist);
		}
		ConnectionMode mode = C4ClientConfiguration.getInstance()
		        .getConnectionMode();
		if (!connlist.isEmpty())
		{
			return connlist.removeFirst();
		}
		if (connlist.isEmpty())
		{
			switch (mode)
			{
				case HTTP:
				{
					connection = new HTTPProxyConnection(auth);
					return connection;
				}
				default:
				{
					break;
				}
			}
		}
		return null;
	}

	public ProxyConnection getClientConnectionByAuth(C4ServerAuth auth,
	        HTTPRequestEvent event)
	{
		return getClientConnectionByAuth(auth);
	}

	public ProxyConnection[] getDualClientConnection(HTTPRequestEvent event)
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
		ProxyConnection[] conns = new ProxyConnection[2];
		conns[0] = getClientConnectionByAuth(auth, event);
		conns[1] = getClientConnectionByAuth(auth, event);
		return conns;
	}

}
