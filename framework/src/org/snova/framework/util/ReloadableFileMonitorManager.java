/**
 * 
 */
package org.snova.framework.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * @author wqy
 * 
 */
public class ReloadableFileMonitorManager implements Runnable
{
	private static ReloadableFileMonitorManager	instance	= new ReloadableFileMonitorManager();
	
	private Map<ReloadableFileMonitor, Long>	  monitorFiles	= new ConcurrentHashMap<ReloadableFileMonitor, Long>();
	
	private ReloadableFileMonitorManager()
	{
		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 5, 5,
		        TimeUnit.SECONDS);
	}
	
	public static ReloadableFileMonitorManager getInstance()
	{
		return instance;
	}
	
	public synchronized void registerConfigFile(ReloadableFileMonitor cfg)
	{
		long currentModTime = cfg.getMonitorFile().lastModified();
		monitorFiles.put(cfg, currentModTime);
	}
	
	private void verifyConfigurationModified()
	{
		for (Map.Entry<ReloadableFileMonitor, Long> entry : monitorFiles
		        .entrySet())
		{
			ReloadableFileMonitor cfg = entry.getKey();
			if (cfg.getMonitorFile().lastModified() != entry.getValue())
			{
				entry.setValue(cfg.getMonitorFile().lastModified());
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
