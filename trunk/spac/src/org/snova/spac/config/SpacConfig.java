/**
 * 
 */
package org.snova.spac.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.arch.config.IniProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author qiyingwang
 *
 */
public class SpacConfig
{
	private static SpacConfig instance =new SpacConfig();
	protected static Logger	     logger	           = LoggerFactory
            .getLogger(SpacConfig.class);
	static
	{
		InputStream is = SpacConfig.class.getResourceAsStream("/spac.conf");
		try
        {
	        instance.props.load(is);
        }
        catch (IOException e)
        {
        	logger.error("Failed to load spac.conf", e);
        }
	}
	
	private IniProperties props = new IniProperties();
	private List<String> httpsOnlySites = null; 
	private SpacConfig(){}
	
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
		if(null == httpsOnlySites)
		{
			String k = props.getProperty("Hosts", "HttpsOnlySites");
			if(null != k)
			{
				String[] splits = k.split("[,|;|\\|]");
				httpsOnlySites = Arrays.asList(splits);
			}
		}
		if(null != httpsOnlySites)
		{
			for(String site:httpsOnlySites)
			{
				if(host.indexOf(site) != -1)
				{
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isScriptSubscribed()
	{
		String s =  props.getProperty("Script", "Subscribe");
		if(null != s)
		{
			return s.equalsIgnoreCase("true");
		}
		return false;
	}
}
