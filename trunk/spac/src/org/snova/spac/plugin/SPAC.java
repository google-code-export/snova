/**
 * 
 */
package org.snova.spac.plugin;

import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.spac.handler.SpacProxyEventHandler;
import org.snova.spac.handler.forward.DirectProxyEventHandler;
import org.snova.spac.script.CSLApiImpl;
import org.snova.spac.script.Commands;
import org.tykedog.csl.interpreter.CSL;

/**
 * @author qiyingwang
 * 
 */
public class SPAC implements Plugin
{
	protected static Logger logger = LoggerFactory.getLogger(SPAC.class);

	@Override
	public void onLoad(PluginContext context) throws Exception
	{

	}

	@Override
	public void onActive(PluginContext context) throws Exception
	{
		CSL csl = CSL.Builder
		        .build(getClass().getResourceAsStream("/spac.csl"));
		csl.setCalculator(new CSLApiImpl());
		csl.setComparator(new CSLApiImpl());
		csl.addFunction(Commands.INT);
		csl.addFunction(Commands.GETHEADER);
		csl.addFunction(Commands.PRINT);
		csl.addFunction(Commands.GETRESCODE);
		csl.addFunction(Commands.SYSTEM);
		csl.addFunction(Commands.LOG);
		EventDispatcher.getSingletonInstance().registerNamedEventHandler(new SpacProxyEventHandler());
		EventDispatcher.getSingletonInstance().registerNamedEventHandler(new DirectProxyEventHandler());

		final CSL tmp = csl;
		try
		{
			tmp.invoke("onInit", null);
		}
		catch (Exception e)
		{
			// TODO: handle exception
		}
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				long waittime = 10 * 1000;
				while (true)
				{
					try
					{
						Thread.sleep(waittime);
						Integer nextwait = (Integer) tmp.invoke("onRoutine",
						        null);
						waittime = nextwait.longValue();
						if (waittime < 0)
						{
							return;
						}
						waittime *= 1000;
					}
					catch (Exception e)
					{
						logger.error("", e);
					}

				}

			}
		}).start();
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
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void onStop() throws Exception
    {
	    // TODO Auto-generated method stub
	    
    }
}
