/**
 * This file is part of the hyk-proxy-framework project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Framework.java 
 *
 * @author yinqiwen [ 2010-8-12 | 09:28:05 PM]
 *
 */
package org.snova.framework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.server.ProxyServer;
import org.snova.framework.trace.Trace;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 *
 */
public class Snova
{
	protected Logger	logger	  = LoggerFactory.getLogger(getClass());
	private ProxyServer	server;
	private boolean	    isStarted	= false;
	
	public Snova(Trace trace)
	{
		SharedObjectHelper.setTrace(trace);
		HostsService.init();
		if(!GAE.init())
		{
			
		}
	}
	
	public void stop()
	{
		try
		{
			if (null != server)
			{
				server.close();
				server = null;
			}
			isStarted = false;
		}
		catch (Exception e)
		{
			logger.error("Failed to stop framework.", e);
		}
		
	}
	
	public boolean isStarted()
	{
		return isStarted;
	}
	
	public boolean start()
	{
		return restart();
	}
	
	public boolean restart()
	{
		try
		{
			stop();
			String listen = SnovaConfiguration.getInstance().getIniProperties()
			        .getProperty("LocalServer", "Listen");
			SimpleSocketAddress address = HttpClientHelper.getHttpRemoteAddress(false, listen);
			server = new ProxyServer(address);
			
			SharedObjectHelper.getTrace().info(
			        "Local HTTP(S) Server Running...\nat " + listen);
			
			isStarted = true;
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to launch local proxy server.", e);
		}
		return false;
	}
}
