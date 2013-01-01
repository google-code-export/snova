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

import java.util.ArrayList;
import java.util.List;

import org.arch.config.IniProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.CommonEvents;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.proxy.google.Google;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.proxy.spac.SPAC;
import org.snova.framework.server.ProxyServer;
import org.snova.framework.server.ProxyServerType;
import org.snova.framework.trace.Trace;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 *
 */
public class Snova
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private List<ProxyServer> servers = new ArrayList<ProxyServer>();
	private boolean isStarted = false;

	public Snova(Trace trace)
	{
		SharedObjectHelper.setTrace(trace);
		CommonEvents.init(null, false);
		HostsService.init();
		Google.init();
		C4.init();
		GAE.init();
		SPAC.init();
	}

	public void stop()
	{
		if (servers.isEmpty())
		{
			return;
		}
		try
		{
			for (ProxyServer s : servers)
			{
				s.close();
			}
			servers.clear();
			SharedObjectHelper.getTrace()
			        .info("Local HTTP(S) Servers stoped\n");
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
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			String listen = cfg.getProperty("LocalServer", "Listen");
			SimpleSocketAddress address = HttpClientHelper
			        .getHttpRemoteAddress(false, listen);
			servers.add(new ProxyServer(address, ProxyServerType.AUTO));
			SharedObjectHelper.getTrace().info(
			        "Local HTTP(S) Server Listen on address " + listen);
			if (C4.enable && null != cfg.getProperty("C4", "Listen"))
			{
				SimpleSocketAddress tmp = HttpClientHelper
				        .getHttpRemoteAddress(false,
				                cfg.getProperty("C4", "Listen"));
				servers.add(new ProxyServer(tmp, ProxyServerType.C4));
				SharedObjectHelper.getTrace().info(
				        "Local HTTP(S) Server Listen on address " + tmp);
			}
			if (GAE.enable && null != cfg.getProperty("GAE", "Listen"))
			{
				SimpleSocketAddress tmp = HttpClientHelper
				        .getHttpRemoteAddress(false,
				                cfg.getProperty("GAE", "Listen"));
				servers.add(new ProxyServer(tmp, ProxyServerType.GAE));
				SharedObjectHelper.getTrace().info(
				        "Local HTTP(S) Server Listen on address " + tmp);
			}

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
