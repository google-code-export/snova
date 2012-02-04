/**
 * 
 */
package org.snova.spac.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;

import javax.imageio.stream.FileImageInputStream;

import org.arch.config.IniProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.ReloadableConfiguration;
import org.snova.framework.config.ReloadableConfigurationMonitor;

/**
 * @author qiyingwang
 * 
 */
public class SpacConfig implements ReloadableConfiguration
{
	private static SpacConfig	instance	 = new SpacConfig();
	protected static Logger	  logger	     = LoggerFactory
	                                                 .getLogger(SpacConfig.class);
	
	private IniProperties	  props	         = new IniProperties();
	private List<String>	  httpsOnlySites	= null;
	
	private SpacConfig()
	{
		reload();
		ReloadableConfigurationMonitor.getInstance().registerConfigFile(this);
	}
	
	public static SpacConfig getInstance()
	{
		return instance;
	}
	
	public String getIPV4HostsSource()
	{
		return props.getProperty("Hosts", "IPV4Source");
	}
	
	public String getIPV6HostsSource()
	{
		return props.getProperty("Hosts", "IPV6Source");
	}
	
	public String getScriptSource()
	{
		return props.getProperty("Script", "Source");
	}
	
	public String getGFWListSource()
	{
		return props.getProperty("GFWList", "Source");
	}
	
	public boolean isHttpsOnlyHost(String host)
	{
		if (null == httpsOnlySites)
		{
			String k = props.getProperty("Hosts", "HttpsOnlySites");
			if (null != k)
			{
				String[] splits = k.split("[,|;|\\|]");
				httpsOnlySites = Arrays.asList(splits);
			}
		}
		if (null != httpsOnlySites)
		{
			for (String site : httpsOnlySites)
			{
				if (host.indexOf(site) != -1)
				{
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isScriptSubscribed()
	{
		String s = props.getProperty("Script", "Subscribe");
		if (null != s)
		{
			return s.equalsIgnoreCase("true");
		}
		return false;
	}
	
	@Override
	public void reload()
	{
		// TODO Auto-generated method stub
		try
		{
			IniProperties tmp = new IniProperties();
			tmp.load(new FileInputStream(getConfigurationFile()));
			props = tmp;
		}
		catch (Exception e)
		{
			logger.error("Failed to parse space.conf", e);
		}
	}
	
	@Override
	public File getConfigurationFile()
	{
		String file = getClass().getResource("/spac.conf").getFile();
		try
		{
			file = URLDecoder.decode(file, "UTF-8");
			return new File(file);
		}
		catch (Exception e)
		{
			//
		}
		
		return null;
	}
}
