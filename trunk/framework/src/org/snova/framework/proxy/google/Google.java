package org.snova.framework.proxy.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;

public class Google
{
	protected static Logger	logger	= LoggerFactory.getLogger(Google.class);
	public static boolean	enable;
	
	static class GoogleRemoteProxyManager implements RemoteProxyManager
	{
		@Override
		public String getName()
		{
			return "Google";
		}
		
		@Override
		public RemoteProxyHandler createProxyHandler()
		{
			return new GoogleRemoteHandler();
		}
	}
	
	public static boolean init()
	{
		
		if (!GoogleConfig.init())
		{
			return false;
		}
		logger.info("Google init.");
		
		RemoteProxyManagerHolder
		        .registerRemoteProxyManager(new GoogleRemoteProxyManager());
		enable = true;
		return true;
	}
}
