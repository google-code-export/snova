package org.snova.framework.proxy.gae;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;

public class GAE
{
	protected static Logger	logger	= LoggerFactory.getLogger(GAE.class);
	
	static class GAERemoteProxyManager implements RemoteProxyManager
	{
		@Override
		public String getName()
		{
			return "GAE";
		}
		
		@Override
		public RemoteProxyHandler createProxyHandler()
		{
			return new GAERemoteHandler();
		}
	}
	
	public static boolean init()
	{
		RemoteProxyManagerHolder
		        .registerRemoteProxyManager(new GAERemoteProxyManager());
		
		return true;
	}
	
}
