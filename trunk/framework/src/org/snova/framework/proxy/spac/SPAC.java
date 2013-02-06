package org.snova.framework.proxy.spac;

import java.io.IOException;
import java.util.Arrays;

import org.arch.config.IniProperties;
import org.arch.misc.crypto.base64.Base64;
import org.arch.util.ArraysHelper;
import org.arch.util.NetworkHelper;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.forward.ForwardRemoteProxyManager;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.proxy.spac.filter.GFWList;
import org.snova.framework.proxy.spac.filter.SpacFilter;
import org.snova.framework.server.ProxyHandler;
import org.snova.framework.server.ProxyServerType;
import org.snova.framework.util.FileManager;
import org.snova.framework.util.MiscHelper;
import org.snova.framework.util.SharedObjectHelper;

public class SPAC
{
	protected static Logger	logger	   = LoggerFactory.getLogger(SPAC.class);
	public static boolean	enable;
	public static boolean	spacEnbale	= true;
	
	private static void fetchGFWList()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String gfwlist = MiscHelper.fetchContent(cfg.getProperty("SPAC",
		        "GFWList"));
		if (null != gfwlist)
		{
			FileManager.writeFile(Base64.decode(gfwlist.trim()),
			        "spac/snova-gfwlist.txt");
		}
	}
	
	private static void initGFWList()
	{
		fetchGFWList();
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String gfwlist = cfg.getProperty("SPAC", "GFWList");
		if (null == gfwlist)
		{
			return;
		}
		String content1 = FileManager.loadFile("spac/snova-gfwlist.txt");
		String content2 = FileManager.loadFile("spac/user-gfwlist.txt");
		String all = content1 + "\n" + content2;
		try
		{
			GFWList.loadRules(all);
			GFWList.generatePAC(gfwlist, all);
		}
		catch (IOException e)
		{
			logger.error("", e);
		}
	}
	
	private static void fetchIPRangeFile()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String iprange = MiscHelper.fetchContent(cfg.getProperty("SPAC",
		        "IPRangeRepo"));
		if (null != iprange)
		{
			FileManager.writeFile(iprange, "spac/iprange.txt");
		}
	}
	
	private static void initIPRange()
	{
		fetchIPRangeFile();
	}
	
	public static boolean init()
	{
		RemoteProxyManagerHolder
		        .registerRemoteProxyManager(new ForwardRemoteProxyManager());
		if (!SpacConfig.init())
		{
			spacEnbale = false;
			return false;
		}
		logger.info("SPAC init.");
		enable = true;
		SharedObjectHelper.getGlobalThreadPool().submit(new Runnable()
		{
			public void run()
			{
				initGFWList();
				initIPRange();
			}
		});
		SpacFilter.init();
		return true;
	}
	
	public static RemoteProxyManager[] selectProxy(HttpRequest req,
	        ProxyServerType serverType, Object[] attr, ProxyHandler h)
	{
		if (req.getUri().endsWith("/pac/gfwlist"))
		{
			String host = HttpHeaders.getHost(req);
			if (host.contains(":"))
			{
				host = host.split(":", 2)[0];
			}
			if (NetworkHelper.isPrivateIP(host))
			{
				
			}
		}
		
		String[] proxyAattr = new String[0];
		switch (serverType)
		{
			case GAE:
			{
				return new RemoteProxyManager[] { new GAE.GAERemoteProxyManager() };
			}
			case C4:
			{
				return new RemoteProxyManager[] { new C4.C4RemoteProxyManager() };
			}
			default:
			{
				break;
			}
		}
		String[] proxy = new String[] { SpacConfig.defaultProxy };
		if (spacEnbale)
		{
			boolean found = false;
			for (SpacRule rule : SpacConfig.spacRules)
			{
				if (rule.match(req, h.isHttps()))
				{
					proxy = rule.proxyies;
					proxyAattr = rule.attrs;
					found = true;
					for (String ar : proxyAattr)
					{
						if (ar.equalsIgnoreCase("RedirectHttps"))
						{
							DefaultHttpResponse res = new DefaultHttpResponse(
							        HttpVersion.HTTP_1_1,
							        HttpResponseStatus.FOUND);
							String url = req.getUri();
							if (url.startsWith("http://"))
							{
								url = url.replace("http://", "https://");
							}
							else
							{
								url = "https://" + HttpHeaders.getHost(req)
								        + url;
							}
							res.setHeader("Location", url);
							res.setHeader("Connection", "close");
							h.handleResponse(null, res);
							h.close();
							return null;
						}
					}
					break;
				}
				else
				{
					
				}
			}
			if (!found)
			{
				if (HostsService.isDirectReachable(req))
				{
					proxy = new String[] { "Direct", SpacConfig.defaultProxy };
				}
			}
		}
		// logger.info("Select " + Arrays.toString(proxy) + " for " +
		// MiscHelper.getURLString(req, false));
		RemoteProxyManager[] rms = new RemoteProxyManager[proxy.length];
		for (int i = 0; i < rms.length; i++)
		{
			String name = proxy[i];
			if (name.equalsIgnoreCase("Default"))
			{
				name = SpacConfig.defaultProxy;
			}
			else if (name.equalsIgnoreCase("Direct"))
			{
				name = "Forward";
			}
			else if (name.startsWith("http://") || name.startsWith("socks://"))
			{
				String addon = name;
				name = "Forward";
				proxyAattr = ArraysHelper.append(proxyAattr, addon);
			}
			else if (name.equalsIgnoreCase("GoogleHttps")
			        || name.equalsIgnoreCase("GoogleHttp"))
			{
				name = "Google";
				String attrtmp = "HTTPS";
				if (name.equalsIgnoreCase("GoogleHttp"))
				{
					attrtmp = "HTTP";
				}
				proxyAattr = ArraysHelper.append(proxyAattr, attrtmp);
			}
			rms[i] = RemoteProxyManagerHolder.getRemoteProxyManager(name);
		}
		attr[0] = proxyAattr;
		if (null == rms || rms.length == 0)
		{
			logger.error("No proxy service found for " + req.getHeader("Host"));
			h.close();
			return null;
		}
		return rms;
	}
}
