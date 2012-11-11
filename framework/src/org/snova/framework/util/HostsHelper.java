/**
 * 
 */
package org.snova.framework.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import javax.naming.NamingException;

import org.arch.dns.ResolveOptions;
import org.arch.dns.Resolver;
import org.arch.util.ListSelector;
import org.snova.framework.config.DesktopFrameworkConfiguration;

/**
 * @author qiyingwang
 * 
 */
public class HostsHelper
{
	private static Map<String, ListSelector<String>>	hostsMappingTable	= new HashMap<String, ListSelector<String>>();
	
	static
	{
		InputStream is = HostsHelper.class.getResourceAsStream("/hosts.conf");
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
	
	public static String lookupIP(String host)
	{
		ResolveOptions options = new ResolveOptions();
		options.useTcp = true;
		options.cacheTtl = ResolveOptions.DNS_CACHE_TTL_SELF;
		String[] ips;
		try
		{
			DesktopFrameworkConfiguration cfg = DesktopFrameworkConfiguration
			        .getInstance();
			ips = Resolver.resolveIPv4(cfg.getTrsutedDNS(), host, options);
			if (ips.length > 0)
			{
				if (ips.length == 1)
				{
					return ips[0];
				}
				Random r = new Random();
				return ips[r.nextInt(ips.length)];
			}
		}
		catch (NamingException e)
		{
			
		}
		return host;
	}
	
	public static String getMappingHost(String host)
	{
		ListSelector<String> selector = hostsMappingTable.get(host);
		if (null == selector)
		{
			return host;
		}
		return selector.select().trim();
	}
	
	public static void removeMapping(String host, String mapping)
	{
		ListSelector<String> selector = hostsMappingTable.get(host);
		if (null != selector)
		{
			selector.remove(mapping);
		}
	}
}
