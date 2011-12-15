package org.snova.gae.client.plugin;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.GUIPlugin;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.gae.client.admin.GAEAdmin;
import org.snova.gae.client.config.GAEClientConfiguration;
import org.snova.gae.client.config.GAEClientConfiguration.GAEServerAuth;
import org.snova.gae.client.config.GAEClientConfiguration.ProxyInfo;
import org.snova.gae.client.config.GAEClientConfiguration.ProxyType;
import org.snova.gae.client.connection.ProxyConnection;
import org.snova.gae.client.connection.ProxyConnectionManager;
import org.snova.gae.client.handler.ClientProxyEventHandler;
import org.snova.gae.client.shell.swing.GAEConfigPanel;
import org.snova.gae.client.shell.swing.GAEImageUtil;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.event.GAEEvents;
import org.snova.gae.common.event.RequestSharedAppIDEvent;
import org.snova.gae.common.event.RequestSharedAppIDResultEvent;

public class GAE implements GUIPlugin
{
	protected static Logger logger = LoggerFactory.getLogger(GAE.class);
	ClientProxyEventHandler handler = new ClientProxyEventHandler();

	@Override
	public void onLoad(PluginContext context) throws Exception
	{

	}

	public static boolean initProxyConnections(List<GAEServerAuth> auths)
	{
		if (!ProxyConnectionManager.getInstance().init(auths))
		{
			ProxyInfo info = GAEClientConfiguration.getInstance()
			        .getLocalProxy();
			if (null == info || info.host == null)
			{
				// try GoogleCN
				info = new ProxyInfo();
				info.host = GAEConstants.RESERVED_GOOGLECN_HOST_MAPPING;
				info.port = 80;
				GAEClientConfiguration.getInstance().setLocalProxy(info);
				return initProxyConnections(auths);
			}
			else if (info.host == GAEConstants.RESERVED_GOOGLECN_HOST_MAPPING)
			{
				// try GoogleHttps
				info = new ProxyInfo();
				info.host = GAEConstants.RESERVED_GOOGLEHTTPS_HOST_MAPPING;
				info.port = 443;
				info.type = ProxyType.HTTPS;
				GAEClientConfiguration.getInstance().setLocalProxy(info);
				return initProxyConnections(auths);
			}
			else
			{
				logger.error("Failed to connect GAE server.");
				return false;
			}
		}
		else
		{
			// ProxyInfo info = GAEClientConfiguration.getInstance()
			// .getLocalProxy();
			// PreferenceHelper.savePreference(GAEConstants.PREFERED_GOOGLE_PROXY,
			// info != null ? info.type + ":" + info.host + ":"
			// + info.port : "");
			return true;
		}
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
					auth.user = GAEConstants.ANONYMOUSE_NAME;
					auth.passwd = GAEConstants.ANONYMOUSE_NAME;
					sharedAuths.add(auth);
				}
				initProxyConnections(sharedAuths);
				return;
			}
		}
		initProxyConnections(auths);
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
    public ImageIcon getIcon()
    {
		 return GAEImageUtil.APPENGINE;
    }

	@Override
    public JPanel getConfigPanel()
    {
	    return panel;
    }
	
	private GAEConfigPanel panel =  new GAEConfigPanel();
}
