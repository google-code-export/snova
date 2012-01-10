/**
 * 
 */
package org.snova.spac.service;

import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.Proxy.Type;
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
public class ScriptService implements Runnable
{
	protected static Logger	     logger	           = LoggerFactory
            .getLogger(HostsService.class);
	private static ScriptService instance = new ScriptService();
	
	public static ScriptService getInstance()
	{
		return instance;
	}
	
	private ScriptService()
	{
		if(SpacConfig.getInstance().isScriptSubscribed())
		{
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 10, 7200, TimeUnit.SECONDS);
		}
	}
	
	private boolean downloadFile(Proxy proxy, URL url, File file)
	{
		try
        {
			URLConnection conn = null;
			if(null == proxy)
			{
				conn = url.openConnection();
			}
			else
			{
				conn = url.openConnection(proxy);
			}
			conn.connect();
			File destFile = new File(file.getAbsolutePath() + ".update");;
			FileOutputStream fos = new FileOutputStream(destFile);
			byte[] buffer = new byte[2048];
			while(true)
			{
				int len = conn.getInputStream().read(buffer);
				if(len < 0)
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
	
	
	@Override
    public void run()
    {
	   
		String url = SpacConfig.getInstance().getScriptSource();
		if(null == url) return ;
		try
        {
			String file = getClass().getResource("/spac.td").getFile();
			file = URLDecoder.decode(file, "UTF-8");
	        if(!downloadFile(null, new URL(url), new File(file)))
	        {
	        	FrameworkConfiguration cfg = DesktopFrameworkConfiguration.getInstance();
	        	Proxy p = new Proxy(Type.HTTP, new InetSocketAddress(cfg.getLocalProxyServerAddress().host, cfg.getLocalProxyServerAddress().port));
	        	downloadFile(p, new URL(url), new File(file));
	        }
        }
        catch (Exception e)
        {
        	logger.error("Failed to download script file!", e);
        }
    }

}
