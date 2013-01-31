package org.snova.framework.proxy.spac;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;

import org.arch.config.IniProperties;
import org.arch.misc.crypto.base64.Base64;
import org.arch.util.ArraysHelper;
import org.arch.util.NetworkHelper;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.forward.ForwardRemoteProxyManager;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.proxy.spac.filter.GFWList;
import org.snova.framework.proxy.spac.filter.SpacFilter;
import org.snova.framework.server.ProxyServerType;
import org.snova.framework.util.FileManager;
import org.snova.framework.util.MiscHelper;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

public class SPAC {
	protected static Logger logger = LoggerFactory.getLogger(SPAC.class);
	public static boolean enable;

	private static void fetchGFWList()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String gfwlist = MiscHelper.fetchContent(cfg.getProperty("SPAC", "GFWList"));
		if(null != gfwlist)
		{
			gfwlist = new String(Base64.decodeFast(gfwlist));
			FileManager.writeFile(gfwlist, "spac/snova-gfwlist.txt");
		}
		
	}
	
	private static void initGFWList()
	{
		fetchGFWList();
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String gfwlist = MiscHelper.fetchContent(cfg.getProperty("SPAC", "GFWList"));
		String content1 = FileManager.loadFile("spac/snova-gfwlist.txt");
		String content2 = FileManager.loadFile("spac/user-gfwlist.txt");
		String all = content1 + "\n" + content2;
		try {
			GFWList.generatePAC(gfwlist, all);
		} catch (IOException e) {
			e.printStackTrace();
		}
		GFWList.loadRules(all);
	}

	private static void fetchIPRangeFile() {
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String iprange = MiscHelper.fetchContent(cfg.getProperty("SPAC", "IPRangeRepo"));
		if(null != iprange)
		{
			FileManager.writeFile(iprange, "spac/iprange.txt");
		}
	}
	
	private static void initIPRange()
	{
		fetchGFWList();
	}

	public static boolean init() {
		RemoteProxyManagerHolder
				.registerRemoteProxyManager(new ForwardRemoteProxyManager());
		if (!SpacConfig.init()) {
			return false;
		}
		logger.info("SPAC init.");
		enable = true;
		SharedObjectHelper.getGlobalThreadPool().submit(new Runnable() {
			public void run() {
				initGFWList();
				initIPRange();
			}
		});
		SpacFilter.init();
		return true;
	}

	public static RemoteProxyManager[] selectProxy(HttpRequest req,
			ProxyServerType serverType, Object[] attr) {
		String[] proxyAattr = new String[0];
		switch (serverType) {
		case GAE: {
			return new RemoteProxyManager[] { new GAE.GAERemoteProxyManager() };
		}
		case C4: {
			return new RemoteProxyManager[] { new C4.C4RemoteProxyManager() };
		}
		default: {
			break;
		}
		}
		String[] proxy = new String[] { SpacConfig.defaultProxy };
		for (SpacRule rule : SpacConfig.spacRules) {
			if (rule.match(req)) {
				proxy = rule.proxyies;
				proxyAattr = rule.attrs;
				break;
			} else {

			}
		}

		System.out.println("@@@Match result " + Arrays.toString(proxyAattr)
				+ " for " + req.getHeader("Host"));
		RemoteProxyManager[] rms = new RemoteProxyManager[proxy.length];
		for (int i = 0; i < rms.length; i++) {
			String name = proxy[i];
			if (name.equalsIgnoreCase("Default")) {
				name = SpacConfig.defaultProxy;
			} else if (name.equalsIgnoreCase("Direct")) {
				name = "Forward";
			} else if (name.startsWith("http://")
					|| name.startsWith("socks://")) {
				String addon = name;
				name = "Forward";
				proxyAattr = ArraysHelper.append(proxyAattr, addon);
			} else if (name.equalsIgnoreCase("GoogleHttps")
					|| name.equalsIgnoreCase("GoogleHttp")) {
				name = "Google";
				String attrtmp = "HTTPS";
				if (name.equalsIgnoreCase("GoogleHttp")) {
					attrtmp = "HTTP";
				}
				proxyAattr = ArraysHelper.append(proxyAattr, attrtmp);
			}
			rms[i] = RemoteProxyManagerHolder.getRemoteProxyManager(name);
			System.out.println("######" + rms[i] + " for " + name + " for "
					+ req.getHeader("Host"));
		}
		attr[0] = proxyAattr;
		return rms;
	}
}
