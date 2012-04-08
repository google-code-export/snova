/**
 * 
 */
package org.snova.spac.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.DesktopFrameworkConfiguration;
import org.snova.framework.config.FrameworkConfiguration;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.spac.config.SpacConfig;

/**
 * @author qiyingwang
 * 
 */
public class HostsService implements Runnable
{
	private static final String	IPV4_HOSTS_FILE	 = "/ipv4_hosts";
	private static final String	IPV6_HOSTS_FILE	 = "/ipv6_hosts";
	protected static Logger	    logger	         = LoggerFactory
	                                                     .getLogger(HostsService.class);
	private static HostsService	instance	     = new HostsService();
	
	private Map<String, String>	IPv4MappingTable	= new ConcurrentHashMap<String, String>();
	private Map<String, String>	IPv6MappingTable	= new ConcurrentHashMap<String, String>();
	
	private HostsService()
	{
		loadHostsMappingFile();
		if (SpacConfig.getInstance().isHostsSubscribed())
		{
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 10,
			        7200, TimeUnit.SECONDS);
		}	
	}
	
	private boolean loadHostsMapping(InputStream is, Map<String, String> table)
	{
		try
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line = br.readLine();
			while (line != null)
			{
				line = line.trim();
				if (!line.isEmpty() && !line.startsWith("#"))
				{
					String[] ss = line.split("\\s+");
					table.put(ss[1].trim(), ss[0].trim());
				}
				line = br.readLine();
			}
			is.close();
			return true;
		}
		catch (IOException e)
		{
			//
		}
		return false;
	}
	
	private void loadHostsMappingFile()
	{
		InputStream is = HostsService.class
		        .getResourceAsStream(IPV4_HOSTS_FILE);
		
		Map<String, String> tmp = new ConcurrentHashMap<String, String>();
		if (loadHostsMapping(is, tmp))
		{
			IPv4MappingTable = tmp;
		}
		InputStream ipv6_is = HostsService.class
		        .getResourceAsStream(IPV6_HOSTS_FILE);
		tmp = new ConcurrentHashMap<String, String>();
		if (loadHostsMapping(ipv6_is, tmp))
		{
			IPv6MappingTable = tmp;
		}
	}
	
	private boolean downloadFile(Proxy proxy, URL url, File file)
	{
		try
		{
			URLConnection conn = null;
			if (null == proxy)
			{
				conn = url.openConnection();
			}
			else
			{
				conn = url.openConnection(proxy);
			}
			conn.connect();
			File destFile = new File(file.getAbsolutePath() + ".update");
			;
			FileOutputStream fos = new FileOutputStream(destFile);
			byte[] buffer = new byte[2048];
			while (true)
			{
				int len = conn.getInputStream().read(buffer);
				if (len < 0)
				{
					break;
				}
				else
				{
					fos.write(buffer, 0, len);
				}
			}
			fos.close();
			file.delete();
			destFile.renameTo(file);
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to get remote hosts file.", e);
		}
		
		return false;
	}
	
	private boolean downloadIPV4SmartHosts()
	{
		String url = SpacConfig.getInstance().getIPV4HostsSource();
		if (null == url)
			return false;
		try
		{
			String file = getClass().getResource(IPV4_HOSTS_FILE).getFile();
			file = URLDecoder.decode(file, "UTF-8");
			if (!downloadFile(null, new URL(url), new File(file)))
			{
				FrameworkConfiguration cfg = DesktopFrameworkConfiguration
				        .getInstance();
				Proxy p = new Proxy(Type.HTTP, new InetSocketAddress(
				        cfg.getLocalProxyServerAddress().host,
				        cfg.getLocalProxyServerAddress().port));
				return downloadFile(p, new URL(url), new File(file));
			}
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to download IPv4 hosts file!", e);
		}
		return false;
	}
	
	private boolean downloadIPV6SmartHosts()
	{
		String url = SpacConfig.getInstance().getIPV6HostsSource();
		if (null == url)
			return false;
		try
		{
			String file = getClass().getResource(IPV6_HOSTS_FILE).getFile();
			file = URLDecoder.decode(file, "UTF-8");
			if (!downloadFile(null, new URL(url), new File(file)))
			{
				FrameworkConfiguration cfg = DesktopFrameworkConfiguration
				        .getInstance();
				Proxy p = new Proxy(Type.HTTP, new InetSocketAddress(
				        cfg.getLocalProxyServerAddress().host,
				        cfg.getLocalProxyServerAddress().port));
				return downloadFile(p, new URL(url), new File(file));
			}
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to download IPv6 hosts file!", e);
		}
		return false;
	}
	
	@Override
	public void run()
	{
		if (downloadIPV4SmartHosts() || downloadIPV6SmartHosts())
		{
			loadHostsMappingFile();
		}
	}
	
	private String _getMappingHostIPV4(String host)
	{
		return IPv4MappingTable.get(host);
	}
	
	private String _getMappingHostIPV6(String host)
	{
		return IPv6MappingTable.get(host);
	}
	
	public static String getMappingHostIPV4(String host)
	{
		if (host.indexOf(":") != -1)
		{
			host = host.substring(0, host.indexOf(":"));
		}
		return instance._getMappingHostIPV4(host);
	}
	
	public static String getMappingHostIPV6(String host)
	{
		if (host.indexOf(":") != -1)
		{
			host = host.substring(0, host.indexOf(":"));
		}
		return instance._getMappingHostIPV6(host);
	}
}
