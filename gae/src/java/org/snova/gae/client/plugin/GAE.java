package org.snova.gae.client.plugin;

import java.util.ArrayList;
import java.util.List;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.gae.client.admin.GAEAdmin;
import org.snova.gae.client.config.GAEClientConfiguration;
import org.snova.gae.client.config.GAEClientConfiguration.GAEServerAuth;
import org.snova.gae.client.connection.ProxyConnection;
import org.snova.gae.client.connection.ProxyConnectionManager;
import org.snova.gae.client.handler.ClientProxyEventHandler;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.event.AdminResponseEvent;
import org.snova.gae.common.event.GAEEvents;
import org.snova.gae.common.event.RequestSharedAppIDEvent;
import org.snova.gae.common.event.RequestSharedAppIDResultEvent;

public class GAE implements Plugin
{
	protected static Logger logger = LoggerFactory.getLogger(GAE.class);
	ClientProxyEventHandler handler = new ClientProxyEventHandler();

	@Override
	public void onLoad(PluginContext context) throws Exception
	{

	}

	public static boolean initProxyConnections(List<GAEServerAuth> auths)
	{
		return ProxyConnectionManager.getInstance().init(auths);
	}

	private List<String> fetchSharedAppIDs()
	{
		GAEServerAuth auth = new GAEServerAuth();
		auth.appid = GAEClientConfiguration.getInstance().getMasterNode().appid;
		auth.user = GAEConstants.ANONYMOUSE_NAME;
		auth.passwd = GAEConstants.ANONYMOUSE_NAME;
		ProxyConnection conn = ProxyConnectionManager.getInstance()
		        .getClientConnectionByAuth(auth);
		if (null != conn)
		{
			final List<String> appids = new ArrayList<String>();
			conn.send(new RequestSharedAppIDEvent(), new EventHandler()
			{
				@Override
				public void onEvent(EventHeader header, Event event)
				{
					if (header.type == GAEConstants.REQUEST_SHARED_APPID_RESULT_EVENT_TYPE)
					{
						RequestSharedAppIDResultEvent res = (RequestSharedAppIDResultEvent) event;
						for (String s : res.appids)
						{
							appids.add(s);
						}
					}
					else if(header.type == GAEConstants.ADMIN_RESPONSE_EVENT_TYPE)
					{
						AdminResponseEvent ev = (AdminResponseEvent) event;
						logger.error("Failed to get shared appid:" + ev.errorCause);
					}
					synchronized (appids)
					{
						appids.notify();
					}
				}
			});
			synchronized (appids)
			{
				if (appids.isEmpty())
				{
					try
					{
						appids.wait(60000);
					}
					catch (InterruptedException e)
					{
					}
				}
				return appids;
			}
		}
		return null;
	}

	@Override
	public void onActive(PluginContext context) throws Exception
	{
		ClientProxyEventHandler handler = new ClientProxyEventHandler();
		GAEEvents.init(handler, false);
		
	}

	@Override
	public void onDeactive(PluginContext context) throws Exception
	{

	}

	@Override
	public void onUnload(PluginContext context) throws Exception
	{

	}

	@Override
	public Runnable getAdminInterface()
	{
		return new GAEAdmin();
	}

	@Override
    public void onStart() throws Exception
    {
		List<GAEServerAuth> auths = GAEClientConfiguration.getInstance()
		        .getGAEServerAuths();
		if (null == auths || auths.isEmpty())
		{
			List<String> appids = fetchSharedAppIDs();
			if (null != appids && !appids.isEmpty())
			{
				List<GAEServerAuth> sharedAuths = new ArrayList<GAEServerAuth>();
				for (String appid : appids)
				{
					GAEServerAuth auth = new GAEServerAuth();
					auth.appid = appid;
					auth.backendEnable =  GAEClientConfiguration.getInstance().getMasterNode().backendEnable;
					auth.init();
					sharedAuths.add(auth);
				}
				initProxyConnections(sharedAuths);
				return;
			}
		}
		initProxyConnections(auths);
	    
    }

	@Override
    public void onStop() throws Exception
    {
	    ProxyConnectionManager.getInstance().close();
    }
}
