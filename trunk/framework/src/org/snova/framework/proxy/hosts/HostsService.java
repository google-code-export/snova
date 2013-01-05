/**
 * 
 */
package org.snova.framework.proxy.hosts;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.arch.config.IniProperties;
import org.arch.dns.ResolveOptions;
import org.arch.dns.Resolver;
import org.arch.util.ListSelector;
import org.snova.framework.config.SnovaConfiguration;

/**
 * @author yinqiwen
 * 
 */
public class HostsService
{
	private static Map<String, ListSelector<String>>	hostsMappingTable	= new HashMap<String, ListSelector<String>>();
	private static int	                             enable	              = 1;
	private static String[]	                         trustedDNS	          = new String[] {
	        "8.8.8.8", "208.67.222.222", "8.8.4.4", "208.67.220.220"	  };
	
	private static void loadHostFile(String file)
	{
		InputStream is = HostsService.class.getResourceAsStream("/" + file);
		Properties props = new Properties();
		try
		{
			props.load(is);
			Set<Entry<Object, Object>> entries = props.entrySet();
			for (Entry<Object, Object> entry : entries)
			{
				String key = (String) entry.getKey();
				String value = (String) entry.getValue();
				key = key.trim();
				value = value.trim();
				String[] splits = value.split("[,|;|\\|]");
				List<String> mappings = Arrays.asList(splits);
				hostsMappingTable.put(key, new ListSelector<String>(mappings));
				
			}
			is.close();
		}
		catch (IOException e)
		{
			//
		}
	}
	
	public static String getMappingHost(String host)
	{
		ListSelector<String> selector = hostsMappingTable.get(host);
		
		if (null == selector)
		{
			return host;
		}
		String addr = selector.select().trim();
		if (hostsMappingTable.containsKey(addr))
		{
			return getMappingHost(addr);
		}
		return addr;
	}
	
	public static void removeMapping(String host, String mapping)
	{
		ListSelector<String> selector = hostsMappingTable.get(host);
		if (null != selector)
		{
			selector.remove(mapping);
		}
	}
	
//	public static String getRealHost(String host) throws NamingException
//	{
//		if (enable > 0)
//		{
//			ResolveOptions option = new ResolveOptions();
//			String[] hosts = Resolver.resolveIPv4(trustedDNS, host, option);
//		}
//		return host;
//	}
	
	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		enable = cfg.getIntProperty("Hosts", "Enable", 1);
		loadHostFile("cloud_hosts.conf");
		loadHostFile("user_hosts.conf");
		return true;
	}
}
