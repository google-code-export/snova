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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.FrameworkConfiguration;
import org.snova.framework.event.Events;
import org.snova.framework.httpserver.HttpLocalProxyServer;
import org.snova.framework.plugin.PluginManager;
import org.snova.framework.trace.Trace;
import org.snova.framework.util.SharedObjectHelper;

/**
 *
 */
public class Framework 
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	private HttpLocalProxyServer server;

	private boolean isStarted = false;
	private FrameworkConfiguration config;
	
	public Framework(FrameworkConfiguration config, PluginManager pm, Trace trace)
	{
		this.config = config;
		SharedObjectHelper.setTrace(trace);
		ThreadPoolExecutor workerExecutor = new OrderedMemoryAwareThreadPoolExecutor(
		        config.getThreadPoolSize(), 0, 0);
		SharedObjectHelper.setGlobalThreadPool(workerExecutor);
		
		Events.init(config);
		
		pm.loadPlugins();
		pm.activatePlugins();
		
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
		return restart(config);
	}

	public boolean restart(FrameworkConfiguration cfg)
	{
		try
		{
			stop();
			this.config = cfg;
			//CoreConfiguration config= CoreConfiguration.getInstance();
			server = new HttpLocalProxyServer(
					cfg.getLocalProxyServerAddress(),
			        SharedObjectHelper.getGlobalThreadPool());

			SharedObjectHelper.getTrace().info("Local HTTP(S) Server Running...\nat "
			        + cfg.getLocalProxyServerAddress());
			
			Runnable gcTask = new Runnable()
			{	
				@Override
				public void run()
				{
					System.gc();	
				}
			};
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(gcTask, 10, 10, TimeUnit.SECONDS);
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
