package org.snova.c4.client.plugin;

import java.net.InetAddress;

import org.arch.util.NetworkHelper;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.ConnectionMode;
import org.snova.c4.client.connection.ProxyConnectionManager;
import org.snova.c4.client.handler.C4ClientEventHandler;
import org.snova.c4.common.event.C4Events;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;

public class C4 implements Plugin
{
	protected static Logger	logger	= LoggerFactory.getLogger(C4.class);
	
	@Override
	public void onLoad(PluginContext context) throws Exception
	{
		
	}
	
	@Override
	public void onActive(PluginContext context) throws Exception
	{
		C4Events.init(new C4ClientEventHandler(), false);
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
		return null;
	}
	
	@Override
	public void onStart() throws Exception
	{
		ProxyConnectionManager.getInstance().init(
		        C4ClientConfiguration.getInstance().getC4ServerAuths());
	}
	
	@Override
	public void onStop() throws Exception
	{
	}
}
