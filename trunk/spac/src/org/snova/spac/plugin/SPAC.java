/**
 * 
 */
package org.snova.spac.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;

import org.antlr.runtime.RecognitionException;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.spac.handler.SpacProxyEventHandler;
import org.snova.spac.script.TykedogApiImpl;
import org.snova.spac.script.Commands;
import org.snova.spac.service.HostsService;
import org.snova.spac.service.ScriptService;
import org.tykedog.csl.interpreter.CSL;

/**
 * @author qiyingwang
 * 
 */
public class SPAC implements Plugin
{
	protected static Logger logger = LoggerFactory.getLogger(SPAC.class);
	protected long tstamp = -1;

	@Override
	public void onLoad(PluginContext context) throws Exception
	{

	}
	
	private CSL reloadCSL() throws IOException, RecognitionException
	{
		String file = getClass().getResource("/spac.td").getFile();
		file = URLDecoder.decode(file, "UTF-8");
		File f = new File(file);
		long tmp = f.lastModified();
		if(tmp != tstamp)
		{
			tstamp = tmp;
		}
		InputStream is = getClass().getResourceAsStream("/spac.td");
		CSL csl = CSL.Builder
		        .build(is);
		csl.setCalculator(new TykedogApiImpl());
		csl.setComparator(new TykedogApiImpl());
		csl.addFunction(Commands.INT);
		csl.addFunction(Commands.GETHEADER);
		csl.addFunction(Commands.PRINT);
		csl.addFunction(Commands.GETRESCODE);
		csl.addFunction(Commands.SYSTEM);
		csl.addFunction(Commands.LOG);
		csl.addFunction(Commands.INHOSTS);
		csl.addFunction(Commands.INIPV4);
		csl.addFunction(Commands.INIPV6);
		is.close();
		return csl;
	}

	@Override
	public void onActive(PluginContext context) throws Exception
	{
		
		final SpacProxyEventHandler handler = new SpacProxyEventHandler();
		EventDispatcher.getSingletonInstance().registerNamedEventHandler(handler);

		final CSL tmp = reloadCSL();
		try
		{
			tmp.invoke("OnInit", null);
			handler.setScriptEngine(tmp);
		}
		catch (Exception e)
		{
			logger.error("Failed to invoke OnInit function", e);
		}
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				long waittime = 10 * 1000;
				long routine_period = waittime;
				while (true)
				{
					try
					{
						Thread.sleep(routine_period > 0 ?routine_period:waittime);
						CSL csl = reloadCSL();
						handler.setScriptEngine(csl);
						if(routine_period > 0)
						{
							Integer nextwait = (Integer) tmp.invoke("OnRoutine",
							        null);
							routine_period = nextwait.longValue();
						}
						if (routine_period > 0)
						{
							routine_period *= 1000;
						}
						
					}
					catch (Exception e)
					{
						logger.error("", e);
					}

				}

			}
		}).start();
		
		//just let the services init
		HostsService.getMappingHostIPV4("");
		ScriptService.getInstance();
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
