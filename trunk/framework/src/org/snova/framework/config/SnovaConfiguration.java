/**
 * 
 */
package org.snova.framework.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import org.arch.config.IniProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.common.Constants;
import org.snova.framework.util.ReloadableFileMonitor;
import org.snova.framework.util.ReloadableFileMonitorManager;

/**
 * @author wqy
 * 
 */
public class SnovaConfiguration implements ReloadableFileMonitor
{
	protected static Logger	          logger	 = LoggerFactory
	                                                     .getLogger(SnovaConfiguration.class);
	
	private static SnovaConfiguration	instance	= new SnovaConfiguration();
	
	private IniProperties	          props	     = new IniProperties();
	
	private SnovaConfiguration()
	{
		loadConfig();
		ReloadableFileMonitorManager.getInstance().registerConfigFile(this);
	}
	
	public static SnovaConfiguration getInstance()
	{
		return instance;
	}
	
	public IniProperties getIniProperties()
	{
		return props;
	}
	
	private static File getConfigFile()
	{
		URL url = SnovaConfiguration.class.getResource("/"
		        + Constants.CONF_FILE);
		String conf;
		try
		{
			conf = URLDecoder.decode(url.getFile(), "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			return null;
		}
		return new File(conf);
	}
	
	private void loadConfig()
	{
		InputStream is = SnovaConfiguration.class.getResourceAsStream("/"
		        + Constants.CONF_FILE);
		props = new IniProperties();
		if (null != is)
		{
			try
			{
				props.load(is);
			}
			catch (Exception e)
			{
				logger.error("Failed to load config file:"
				        + Constants.CONF_FILE, e);
			}
		}
	}
	
	public void save()
	{
		File confFile = getConfigFile();
		try
		{
			FileOutputStream fos = new FileOutputStream(confFile);
			props.store(fos);
		}
		catch (Exception e)
		{
			logger.error("Failed to save config file:" + confFile.getName());
		}	
	}
	
	@Override
	public void reload()
	{
		loadConfig();
	}
	
	@Override
	public File getMonitorFile()
	{
		return getConfigFile();
	}
}
