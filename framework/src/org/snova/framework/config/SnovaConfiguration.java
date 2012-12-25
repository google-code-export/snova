/**
 * 
 */
package org.snova.framework.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import org.arch.config.IniProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.common.Constants;
import org.snova.framework.util.ReloadableFileMonitor;
import org.snova.framework.util.ReloadableFileMonitorManager;
import org.snova.framework.util.proxy.ProxyInfo;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author wqy
 * 
 */
public class SnovaConfiguration implements ReloadableFileMonitor
{
	protected static Logger logger = LoggerFactory
	        .getLogger(SnovaConfiguration.class);

	private static SnovaConfiguration instance = new SnovaConfiguration();

	private IniProperties props = new IniProperties();
	private String proxyService = "GAE";

	private SimpleSocketAddress localServerAddress = new SimpleSocketAddress(
	        "localhost", 48100);
	private int threadPoolSize = 30;
	private ProxyInfo localProxy;
	private String[] trsutedDNS = new String[] { "8.8.8.8", "208.67.222.222",
	        "8.8.4.4", "208.67.220.220" };

	public String[] getTrsutedDNS()
	{
		return trsutedDNS;
	}

	private static final String TAG = "Framework";
	private static final String PROXY_TAG = "LocalProxy";
	private static final String PROXY_SERVICE_NAME = "ProxyService";
	private static final String PROXY_SERVER_HOST = "LocalHost";
	private static final String PROXY_SERVER_PORT = "LocalPort";
	private static final String THREAD_POOL_SIZE_NAME = "ThreadPoolSize";
	private static final String PROXY_NAME = "Proxy";
	private static final String TRSUTED_DNS_NAME = "TrsutedDNS";

	private SnovaConfiguration()
	{
		loadConfig();
		ReloadableFileMonitorManager.getInstance().registerConfigFile(this);
	}

	public static SnovaConfiguration getInstance()
	{
		return instance;
	}

	private static File getConfigFile()
	{
		URL url = SnovaConfiguration.class.getResource("/"
		        + Constants.CONF_FILE);
		String conf;
		try
		{
			conf = URLDecoder.decode(url.getFile(), "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			return null;
		}
		return new File(conf);
	}

	private void loadConfig()
	{
		InputStream is = SnovaConfiguration.class.getResourceAsStream("/"
		        + Constants.CONF_FILE);
		if (null != is)
		{

			try
			{
				props = new IniProperties();
				props.load(is);
				proxyService = props.getProperty(TAG, PROXY_SERVICE_NAME,
				        proxyService);
				threadPoolSize = props.getIntProperty(TAG,
				        THREAD_POOL_SIZE_NAME, threadPoolSize);
				localServerAddress.host = props.getProperty(TAG,
				        PROXY_SERVER_HOST, localServerAddress.host);
				localServerAddress.port = props.getIntProperty(TAG,
				        PROXY_SERVER_PORT, localServerAddress.port);

				String dns = props.getProperty(TAG, TRSUTED_DNS_NAME);
				if (null != dns)
				{
					trsutedDNS = dns.trim().split("\\|");
				}

				String s = props.getProperty(PROXY_TAG, PROXY_NAME);
				if (null != s)
				{
					localProxy = new ProxyInfo();
					if (!localProxy.parse(s))
					{
						logger.error("Failed to parse local proxy fro framework.");
						localProxy = null;
					}
				}
			}
			catch (Exception e)
			{
				logger.error("Failed to load config file:"
				        + Constants.CONF_FILE, e);
			}

		}
	}

	public String getProxyManager()
	{
		return proxyService;
	}

	public SimpleSocketAddress getLocalProxyServerAddress()
	{
		return localServerAddress;
	}

	public int getThreadPoolSize()
	{
		return threadPoolSize;
	}

	public void setLocalProxyServerHost(String host)
	{
		this.localServerAddress.host = host;
	}

	public void setLocalProxyServerPort(int port)
	{
		this.localServerAddress.port = port;
	}

	public void setProxyService(String proxyService)
	{
		this.proxyService = proxyService;
	}

	public void setThreadPoolSize(int threadPoolSize)
	{
		this.threadPoolSize = threadPoolSize;
	}

	public ProxyInfo getLocalProxy()
	{
		return localProxy;
	}

	public void setLocalProxy(ProxyInfo info)
	{
		localProxy = info;
	}

	public void save()
	{
		File confFile = getConfigFile();
		try
		{
			FileOutputStream fos = new FileOutputStream(confFile);
			IniProperties props = new IniProperties();
			props.setProperty(TAG, PROXY_SERVICE_NAME, proxyService);
			props.setProperty(TAG, PROXY_SERVER_HOST, localServerAddress.host);
			props.setIntProperty(TAG, PROXY_SERVER_PORT,
			        localServerAddress.port);
			if (null != trsutedDNS)
			{
				StringBuilder buf = new StringBuilder();
				for (int i = 0; i < trsutedDNS.length; i++)
				{
					buf.append(trsutedDNS[i]);
					if (i != trsutedDNS.length - 1)
					{
						buf.append("|");
					}
				}
				props.setProperty(TAG, TRSUTED_DNS_NAME, buf.toString());
			}

			props.setIntProperty(TAG, THREAD_POOL_SIZE_NAME, threadPoolSize);
			if (null != localProxy)
			{
				props.setProperty(PROXY_TAG, PROXY_NAME, localProxy.toString());
			}

			props.store(fos);
		}
		catch (Exception e)
		{
			logger.error("Failed to save config file:" + confFile.getName());
		}

	}

	@Override
	public void reload()
	{
		loadConfig();
	}

	@Override
	public File getMonitorFile()
	{
		return getConfigFile();
	}
}
