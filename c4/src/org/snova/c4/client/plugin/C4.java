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
	
	private void portMapping() throws Exception
	{
		if (null != C4ClientConfiguration.getInstance().getExternalIP())
		{
			return;
		}
		if (NetworkHelper.isPrivateIP(InetAddress.getLocalHost()
		        .getHostAddress()))
		{
			GatewayDiscover gatewayDiscover = new GatewayDiscover();
			gatewayDiscover.discover();
			GatewayDevice activeGW = gatewayDiscover.getValidGateway();
			PortMappingEntry portMapping = new PortMappingEntry();
			int port = C4ClientConfiguration.getInstance().getRServerPort();
			if (activeGW.getSpecificPortMappingEntry(port, "TCP", portMapping))
			{
				logger.info("Port " + port
				        + " is already mapped. Aborting test.");
				return;
			}
			else
			{
				logger.info("Mapping free. Sending port mapping request for port "
				        + port);
				
				// test static lease duration mapping
				if (activeGW.addPortMapping(port, port, activeGW
				        .getLocalAddress().getHostAddress(), "TCP", "Snova-C4"))
				{
					logger.info("Mapping SUCCESSFUL");
					// Thread.sleep(1000*WAIT_TIME);
					//
					// if (activeGW.deletePortMapping(SAMPLE_PORT,"TCP")==true)
					// AddLogline("Port mapping removed, test SUCCESSFUL");
					// else
					// AddLogline("Port mapping removal FAILED");
				}
			}
		}
		
		// logger.error("Port mapping not supported now.");
	}
	
	@Override
	public void onLoad(PluginContext context) throws Exception
	{
		
	}
	
	@Override
	public void onActive(PluginContext context) throws Exception
	{
		C4Events.init(new C4ClientEventHandler(), false);
		if (C4ClientConfiguration.getInstance().getConnectionMode()
		        .equals(ConnectionMode.RSOCKET))
		{
			try
			{
				portMapping();
			}
			catch (Exception e)
			{
				logger.error("Failed to mapping port.", e);
			}
			
		}
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
