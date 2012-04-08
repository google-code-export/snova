/**
 * 
 */
package org.snova.spac.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;

import org.arch.misc.gfw.GFWList;
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
public class GFWListService implements Runnable
{
	private static final String	  GFWLIST_FILE	= "/gfwlist.txt";
	protected static Logger	      logger	   = LoggerFactory
	                                                   .getLogger(GFWListService.class);
	private static GFWListService	instance	= new GFWListService();
	
	private GFWList	              gfwlist	   = null;
	
	private GFWListService()
	{
		loaGFWListFile();
		if(SpacConfig.getInstance().isGFWListSubscribed())
		{
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 10, 7200,
			        TimeUnit.SECONDS);
		}

	}
	
	public boolean _isBlockedByGFW(String url)
	{
		return null == gfwlist ? false : gfwlist.isBlockedByGFW(url);
	}
	
	private void loaGFWListFile()
	{
		InputStream is = GFWListService.class.getResourceAsStream(GFWLIST_FILE);
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		StringBuilder buffer = new StringBuilder();
		try
		{
			while (true)
			{
				String line = br.readLine();
				if (null == line)
				{
					break;
				}
				buffer.append(line);
			}
		}
		catch (Exception e)
		{
			return;
		}
		
		GFWList tmp = GFWList.parse(buffer.toString().trim());
		if (null != tmp)
		{
			gfwlist = tmp;
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
	
	private boolean downloadGFWList()
	{
		String url = SpacConfig.getInstance().getGFWListSource();
		if (null == url)
			return false;
		try
		{
			String file = getClass().getResource(GFWLIST_FILE).getFile();
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
	
	@Override
	public void run()
	{
		if (downloadGFWList())
		{
			loaGFWListFile();
		}
	}
	
	public static boolean isBlockedByGFW(String url)
	{
		return instance._isBlockedByGFW(url);
		
	}
}
