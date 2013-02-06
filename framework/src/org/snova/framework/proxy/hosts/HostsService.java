/**
 * 
 */
package org.snova.framework.proxy.hosts;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.arch.config.IniProperties;
import org.arch.dns.ResolveOptions;
import org.arch.dns.Resolver;
import org.arch.dns.exception.NamingException;
import org.arch.misc.crypto.base64.Base64;
import org.arch.util.ListSelector;
import org.arch.util.StringHelper;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.util.FileManager;
import org.snova.framework.util.MiscHelper;
import org.snova.framework.util.SharedObjectHelper;

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
	private static Map<String, Boolean>	             reachableCache	      = new ConcurrentHashMap<String, Boolean>();
	
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
	
	public static boolean isDirectReachable(HttpRequest req)
	{
		if (enable == 0)
		{
			return false;
		}
		if (enable == 1 && !req.getMethod().equals(HttpMethod.CONNECT))
		{
			return false;
		}
		String host = HttpHeaders.getHost(req);
		String[] ss = host.split(":");
		int port = 80;
		if (ss.length == 2)
		{
			port = Integer.parseInt(ss[1]);
			host = ss[0];
		}
		else
		{
			if (req.getMethod().equals(HttpMethod.CONNECT))
			{
				port = 443;
			}
		}
		String ret = getRealHost(host, port);
		if (ret.equals(host))
		{
			return false;
		}
		return true;
	}
	
	private static boolean isReachable(String host, int port)
	{
		synchronized (reachableCache)
		{
			Boolean v = reachableCache.get(host + port);
			if (null != v)
			{
				return v;
			}
			
		}
		Socket s = new Socket();
		try
		{
			s.connect(new InetSocketAddress(host, port), 5000);
			s.close();
			reachableCache.put(host + port, true);
			return true;
		}
		catch (IOException e)
		{
			
		}
		reachableCache.put(host + port, false);
		return false;
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
	
	public static String getRealHost(String host, int port)
	{
		if (enable > 0)
		{
			ResolveOptions option = new ResolveOptions();
			option.cacheTtl = ResolveOptions.DNS_CACHE_TTL_SELF;
			try
			{
				String[] hosts = Resolver.resolveIPv4(trustedDNS, host, option);
				for (String h : hosts)
				{
					if (isReachable(h, port))
					{
						// System.out.println("#####Get real host:" + h +
						// " for " + host);
						return h;
					}
				}
			}
			catch (NamingException e)
			{
				return host;
			}
		}
		return host;
	}
	
	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		enable = cfg.getIntProperty("Hosts", "Enable", 1);
		String dns = cfg.getProperty("Hosts", "TrustedDNS");
		if (null != dns)
		{
			trustedDNS = dns.split("\\|");
		}
		loadHostFile("cloud_hosts.conf");
		loadHostFile("user_hosts.conf");
		SharedObjectHelper.getGlobalThreadPool().submit(new Runnable()
		{
			public void run()
			{
				IniProperties cfg = SnovaConfiguration.getInstance()
				        .getIniProperties();
				String content = MiscHelper.fetchContent(cfg.getProperty(
				        "Hosts", "CloudHosts"));
				if (!StringHelper.isEmptyString(content))
				{
					FileManager.writeFile(content, "conf/cloud_hosts.conf");
				}
				loadHostFile("cloud_hosts.conf");
				loadHostFile("user_hosts.conf");
			}
		});
		return true;
	}
}
