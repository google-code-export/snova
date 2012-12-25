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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.httpserver.HttpLocalProxyServer;
import org.snova.framework.plugin.PluginManager;
import org.snova.framework.trace.Trace;
import org.snova.framework.util.SharedObjectHelper;

/**
 *
 */
public class Snova
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	private HttpLocalProxyServer server;

	private boolean isStarted = false;
	private SnovaConfiguration config;
	private PluginManager pm;

	public Snova(SnovaConfiguration config, PluginManager pm, Trace trace)
	{
		this.config = config;
		SharedObjectHelper.setTrace(trace);

		pm.loadPlugins();
		pm.activatePlugins();
		this.pm = pm;

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
			pm.stopPlugins();
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

	public boolean restart(SnovaConfiguration cfg)
	{
		try
		{
			stop();
			pm.startPlugins();
			this.config = cfg;
			// CoreConfiguration config= CoreConfiguration.getInstance();
			server = new HttpLocalProxyServer(cfg.getLocalProxyServerAddress(),
			        SharedObjectHelper.getGlobalThreadPool());

			SharedObjectHelper.getTrace().info(
			        "Local HTTP(S) Server Running...\nat "
			                + cfg.getLocalProxyServerAddress());

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
