package org.snova.heroku.client.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.connection.ProxyConnectionManager;
import org.snova.heroku.client.handler.HerokuClientEventHandler;
import org.snova.heroku.common.event.HerokuEvents;

public class Heroku implements Plugin
{
	protected static Logger	logger	= LoggerFactory.getLogger(Heroku.class);
	
	@Override
	public void onLoad(PluginContext context) throws Exception
	{
		
	}
	
	@Override
	public void onActive(PluginContext context) throws Exception
	{
		HerokuEvents.init(new HerokuClientEventHandler(), false);
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
		ProxyConnectionManager.getInstance().init(HerokuClientConfiguration.getInstance().getHerokuServerAuths());
	}
	
	@Override
	public void onStop() throws Exception
	{
	}
}
