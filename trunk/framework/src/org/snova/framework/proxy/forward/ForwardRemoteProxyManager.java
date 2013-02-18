package org.snova.framework.proxy.forward;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;

public class ForwardRemoteProxyManager implements RemoteProxyManager
{
	protected static Logger logger = LoggerFactory
	        .getLogger(ForwardRemoteProxyManager.class);

	@Override
	public String getName()
	{
		return "Forward";
	}

	@Override
	public RemoteProxyHandler createProxyHandler(Map<String, String> attr)
	{
		return new ForwardRemoteHandler(attr);
	}
}
