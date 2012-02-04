/**
 * 
 */
package org.snova.framework.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 * 
 */
public class ReloadableConfigurationMonitor implements Runnable
{
	private static ReloadableConfigurationMonitor	instance	= new ReloadableConfigurationMonitor();
	
	private Map<ReloadableConfiguration, Long>	  monitorFiles	= new ConcurrentHashMap<ReloadableConfiguration, Long>();
	
	private ReloadableConfigurationMonitor()
	{
		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 5, 5,
		        TimeUnit.SECONDS);
	}
	
	public static ReloadableConfigurationMonitor getInstance()
	{
		return instance;
	}
	
	public synchronized void registerConfigFile(ReloadableConfiguration cfg)
	{
		long currentModTime = cfg.getConfigurationFile().lastModified();
		monitorFiles.put(cfg, currentModTime);
	}
	
	private void verifyConfigurationModified()
	{
		for (Map.Entry<ReloadableConfiguration, Long> entry : monitorFiles
		        .entrySet())
		{
			ReloadableConfiguration cfg = entry.getKey();
			if (cfg.getConfigurationFile().lastModified() != entry.getValue())
			{
				entry.setValue(cfg.getConfigurationFile().lastModified());
				cfg.reload();
			}
		}
	}
	
	@Override
	public void run()
	{
		verifyConfigurationModified();
	}
}
