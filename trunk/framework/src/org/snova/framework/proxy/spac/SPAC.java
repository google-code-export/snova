package org.snova.framework.proxy.spac;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.server.ProxyServerType;

public class SPAC
{
	protected static Logger	logger	= LoggerFactory.getLogger(SPAC.class);
	public static boolean	enable;
	
	public static boolean init()
	{
		if (!SpacConfig.init())
		{
			return false;
		}
		logger.info("SPAC init.");
		
		enable = true;
		return true;
	}
	
	public static RemoteProxyManager[] selectProxy(HttpRequest req,
	        ProxyServerType serverType)
	{
		switch (serverType)
		{
			case GAE:
			{
				return new RemoteProxyManager[] { new GAE.GAERemoteProxyManager() };
			}
			case C4:
			{
				return new RemoteProxyManager[] { new C4.C4RemoteProxyManager() };
			}
			case SSH:
			{
				return null;
			}
			default:
			{
				break;
			}
		}
		String[] proxy = new String[] { SpacConfig.defaultProxy };
		for (SpacRule rule : SpacConfig.spacRules)
		{
			if (rule.match(req))
			{
				proxy = rule.proxyies;
				break;
			}
		}
		
		RemoteProxyManager[] rms = new RemoteProxyManager[proxy.length];
		for (int i = 0; i < rms.length; i++)
		{
			String name = proxy[i];
			if (name.equalsIgnoreCase("Default"))
			{
				name = SpacConfig.defaultProxy;
			}
			rms[i] = RemoteProxyManagerHolder.getRemoteProxyManager(name);
		}
		return rms;
	}
}
