/**
 * 
 */
package org.snova.spac.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.antlr.runtime.RecognitionException;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.plugin.Plugin;
import org.snova.framework.plugin.PluginContext;
import org.snova.spac.handler.SpacProxyEventHandler;
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
	protected long tstamp = -1;

	@Override
	public void onLoad(PluginContext context) throws Exception
	{

	}
	
	private CSL reloadCSL() throws IOException, RecognitionException
	{
		String file = getClass().getResource("/spac.csl").getFile();
		File f = new File(file);
		long tmp = f.lastModified();
		if(tmp != tstamp)
		{
			tstamp = tmp;
		}
		InputStream is = getClass().getResourceAsStream("/spac.csl");
		CSL csl = CSL.Builder
		        .build(is);
		csl.setCalculator(new CSLApiImpl());
		csl.setComparator(new CSLApiImpl());
		csl.addFunction(Commands.INT);
		csl.addFunction(Commands.GETHEADER);
		csl.addFunction(Commands.PRINT);
		csl.addFunction(Commands.GETRESCODE);
		csl.addFunction(Commands.SYSTEM);
		csl.addFunction(Commands.LOG);
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
						CSL csl = reloadCSL();
						handler.setScriptEngine(csl);
						Integer nextwait = (Integer) tmp.invoke("OnRoutine",
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
