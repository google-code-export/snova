package org.snova.c4.client.plugin;

import java.net.InetAddress;

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
	protected static Logger logger = LoggerFactory.getLogger(C4.class);

	private void portMapping() throws Exception
	{
		if (null != C4ClientConfiguration.getInstance().getExternalIP())
		{
			return;
		}
		GatewayDiscover discover = new GatewayDiscover();
		logger.info("Looking for Gateway Devices");
		discover.discover();
		GatewayDevice d = discover.getValidGateway();

		if (null != d)
		{
			logger.info("Gateway device found.\n{0} ({1})",
			        new Object[] { d.getModelName(), d.getModelDescription() });
		}
		else
		{
			logger.error("No valid gateway device found.");
			return;
		}
		InetAddress localAddress = d.getLocalAddress();
		logger.info("Using local address: " + localAddress);
		String externalIPAddress = d.getExternalIPAddress();
		logger.info("External address: " + externalIPAddress);
		C4ClientConfiguration.getInstance().setExternalIP(externalIPAddress);
		PortMappingEntry portMapping = new PortMappingEntry();

		int port = C4ClientConfiguration.getInstance().getRServerPort();
		logger.info("Attempting to map port " + port);
		logger.info("Querying device to see if mapping for port " + port
		        + " already exists");

		if (!d.getSpecificPortMappingEntry(port, "TCP", portMapping))
		{
			logger.info("Sending port mapping request");

			if (d.addPortMapping(port, port, localAddress.getHostAddress(),
			        "TCP", "Snova-C4"))
			{
			}
			else
			{
				logger.error("Port mapping removal failed");
				logger.error("Test FAILED");
			}

		}
		else
		{
			logger.info("Port was already mapped. Aborting test.");
		}

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
