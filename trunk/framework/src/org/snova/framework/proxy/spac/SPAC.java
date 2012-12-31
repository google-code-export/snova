package org.snova.framework.proxy.spac;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;
import org.snova.framework.proxy.google.GoogleRemoteHandler;

public class SPAC
{
	protected static Logger logger = LoggerFactory.getLogger(SPAC.class);
	public static boolean enable;

	static class SpacRemoteProxyManager implements RemoteProxyManager
	{
		@Override
		public String getName()
		{
			return "SPAC";
		}

		@Override
		public RemoteProxyHandler createProxyHandler()
		{
			return new GoogleRemoteHandler();
		}
	}

	public static boolean init()
	{

		if (!SpacConfig.init())
		{
			return false;
		}
		logger.info("SPAC init.");

		RemoteProxyManagerHolder
		        .registerRemoteProxyManager(new SpacRemoteProxyManager());
		enable = true;
		return true;
	}
}
