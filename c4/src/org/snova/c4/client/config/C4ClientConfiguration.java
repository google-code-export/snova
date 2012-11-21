/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Config.java 
 *
 * @author yinqiwen [ 2010-5-14 | 08:49:33 PM]
 *
 */
package org.snova.c4.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.arch.config.IniProperties;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptType;
import org.arch.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4PluginVersion;
import org.snova.framework.config.DesktopFrameworkConfiguration;
import org.snova.framework.config.ReloadableConfiguration;
import org.snova.framework.config.ReloadableConfigurationMonitor;
import org.snova.framework.util.proxy.ProxyInfo;

/**
 *
 */
public class C4ClientConfiguration implements ReloadableConfiguration
{
	protected static Logger logger = LoggerFactory
	        .getLogger(C4ClientConfiguration.class);
	private static C4ClientConfiguration instance = new C4ClientConfiguration();

	private static File getConfigFile()
	{
		URL url = C4ClientConfiguration.class.getResource("/"
		        + "c4-client.conf");
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

	private C4ClientConfiguration()
	{
		loadConfig();
		ReloadableConfigurationMonitor.getInstance().registerConfigFile(this);
	}

	private static final String C4_TAG = "C4";
	private static final String WORKER_NODE_NAME = "WorkerNode";
	private static final String CLIENT_TAG = "Client";
	private static final String CONNECTION_MODE_NAME = "ConnectionMode";
	private static final String SIMPLE_URL_ENABLE_NAME = "SimpleURLEnable";
	private static final String COMPRESSOR_NAME = "Compressor";
	private static final String ENCRYPTER_NAME = "Encrypter";
	private static final String HTTP_REQUEST_TIMEOUT_NAME = "HTTPRequestTimeout";
	private static final String MAX_READ_BYTES_NAME = "MaxReadBytes";
	private static final String USE_GLOBAL_PROXY_NAME = "UseGlobalProxy";
	private static final String USER_AGENT_NAME = "UserAgent";

	private static final String APPID_BINDING_TAG = "DomainBinding";

	private void loadConfig()
	{
		try
		{
			FileInputStream fis = new FileInputStream(getConfigFile());
			IniProperties props = new IniProperties();
			props.load(fis);
			Properties ps = props.getProperties(C4_TAG);
			if (null != ps)
			{
				for (Object key : ps.keySet())
				{
					String k = ((String) key).trim();
					if (k.startsWith(WORKER_NODE_NAME))
					{
						C4ServerAuth auth = new C4ServerAuth();
						if (!auth.parse(ps.getProperty(k)))
						{
							throw new Exception("Failed to parse line:" + k
							        + "=" + ps.getProperty(k));
						}
						if (!auth.domain.isEmpty())
						{
							serverAuths.add(auth);
						}
					}
				}
			}

			connectionMode = ConnectionMode.valueOf(props.getProperty(
			        CLIENT_TAG, CONNECTION_MODE_NAME, "HTTP"));
			simpleURLEnable = props.getBoolProperty(CLIENT_TAG,
			        SIMPLE_URL_ENABLE_NAME, false);
			compressor = CompressorType.valueOf(props.getProperty(CLIENT_TAG,
			        COMPRESSOR_NAME, "Snappy").toUpperCase());
			encrypter = EncryptType.valueOf(props.getProperty(CLIENT_TAG,
			        ENCRYPTER_NAME, "SE1"));
			httpRequestTimeout = props.getIntProperty(CLIENT_TAG,
			        HTTP_REQUEST_TIMEOUT_NAME, 25);
			maxReadBytes = props.getIntProperty(CLIENT_TAG,
			        MAX_READ_BYTES_NAME, 512 * 1024);
			useGlobalProxy = props.getBoolProperty(CLIENT_TAG,
			        USE_GLOBAL_PROXY_NAME, false);
			httpProxyUserAgent = props.getProperty(CLIENT_TAG, USER_AGENT_NAME,
			        "Snova-C4 V" + C4PluginVersion.value);

			ps = props.getProperties(APPID_BINDING_TAG);
			if (null != ps)
			{
				appIdBindings.clear();
				for (Object key : ps.keySet())
				{
					String k = ((String) key).trim();
					String v = ps.getProperty(k);
					AppIdBinding binding = new AppIdBinding();
					binding.parse(k, v);
					appIdBindings.add(binding);
				}
			}

		}
		catch (Exception e)
		{
			logger.error("Failed to load gae-client config file!", e);
		}
	}

	public static enum ConnectionMode
	{
		HTTP, RSOCKET;
	}

	public static class C4ServerAuth
	{
		public String domain;
		public int port = 80;

		public void init()
		{
		}

		@Override
		public int hashCode()
		{
			return domain.hashCode() + port;
		}

		@Override
		public boolean equals(Object anObject)
		{
			if (this == anObject)
			{
				return true;
			}
			if (anObject instanceof C4ServerAuth)
			{
				C4ServerAuth anotherString = (C4ServerAuth) anObject;
				if (anotherString.domain.equals(domain)
				        && anotherString.port == port)
				{
					return true;
				}
			}
			return false;
		}

		public boolean parse(String line)
		{
			if (null == line || line.trim().isEmpty())
			{
				return false;
			}
			line = line.trim();
			String[] ss = StringHelper.split(line, ':');
			if (ss.length == 1)
			{
				domain = line;
				port = 80;
			}
			else
			{
				domain = ss[0];
				String portstr = ss[1];
				port = Integer.parseInt(portstr);
			}
			init();
			return true;
		}

		public String toString()
		{
			return domain + (port == 80 ? "" : ":" + port);
		}
	}

	private List<C4ServerAuth> serverAuths = new LinkedList<C4ServerAuth>();

	public void setC4ServerAuths(List<C4ServerAuth> serverAuths)
	{
		this.serverAuths = serverAuths;
	}

	public List<C4ServerAuth> getC4ServerAuths()
	{
		return serverAuths;
	}

	private ConnectionMode connectionMode = ConnectionMode.HTTP;

	public ConnectionMode getConnectionMode()
	{
		return connectionMode;
	}

	public void setConnectionMode(ConnectionMode mode)
	{
		connectionMode = mode;
	}

	private CompressorType compressor;

	public CompressorType getCompressor()
	{
		return compressor;
	}

	public void setCompressor(CompressorType type)
	{
		compressor = type;
	}

	private EncryptType encrypter;

	public EncryptType getEncrypter()
	{
		return encrypter;
	}

	public void setEncrypter(EncryptType type)
	{
		this.encrypter = type;
	}

	private boolean simpleURLEnable;

	public void setSimpleURLEnable(boolean simpleURLEnable)
	{
		this.simpleURLEnable = simpleURLEnable;
	}

	public boolean isSimpleURLEnable()
	{
		return simpleURLEnable;
	}

	private int maxReadBytes = 512 * 1024;

	public int getMaxReadBytes()
	{
		return maxReadBytes;
	}

	public void setMaxReadBytes(int maxReadBytes)
	{
		this.maxReadBytes = maxReadBytes;
	}

	private int httpRequestTimeout = 25;

	public void setHTTPRequestTimeout(int timeout)
	{
		this.httpRequestTimeout = timeout;
	}

	public int getHTTPRequestTimeout()
	{
		return httpRequestTimeout;
	}

	private boolean useGlobalProxy = false;

	public boolean isUseGlobalProxy()
	{
		return useGlobalProxy;
	}

	public void setUseGlobalProxy(boolean useGlobalProxy)
	{
		this.useGlobalProxy = useGlobalProxy;
	}

	public void init() throws Exception
	{
	}

	private List<AppIdBinding> appIdBindings = new LinkedList<C4ClientConfiguration.AppIdBinding>();

	static class AppIdBinding
	{
		String appid;
		List<String> sites = new LinkedList<String>();

		void parse(String appid, String line)
		{
			this.appid = appid;
			String[] ss = line.split("[,|;|\\|]");
			for (String k : ss)
			{
				if (!k.trim().isEmpty())
				{
					sites.add(k);
				}
			}
		}

		void putToIniProperties(IniProperties props)
		{
			StringBuilder buffer = new StringBuilder();
			for (String s : sites)
			{
				buffer.append(s).append("|");
			}
			props.setProperty(APPID_BINDING_TAG, appid, buffer.toString());
		}
	}

	public C4ServerAuth getC4ServerAuth(String domain)
	{
		for (C4ServerAuth auth : serverAuths)
		{
			if (auth.domain.equals(domain))
			{
				return auth;
			}
		}
		return null;
	}

	public String getBindingDomain(String host)
	{
		if (null != appIdBindings)
		{
			for (AppIdBinding binding : appIdBindings)
			{
				for (String site : binding.sites)
				{
					if (host.contains(site))
					{
						return binding.appid.trim();
					}
				}
			}
		}
		return null;
	}

	private String httpProxyUserAgent;

	public String getUserAgent()
	{
		return httpProxyUserAgent;
	}

	public void setUserAgent(String v)
	{
		httpProxyUserAgent = v;
	}

	public static C4ClientConfiguration getInstance()
	{
		return instance;
	}

	public void save() throws Exception
	{
		try
		{
			init();
			FileOutputStream fos = new FileOutputStream(getConfigFile());
			IniProperties props = new IniProperties();
			int i = 0;
			for (C4ServerAuth auth : serverAuths)
			{
				props.setProperty(C4_TAG, WORKER_NODE_NAME + "[" + i + "]",
				        auth.toString());
				i++;
			}

			props.setProperty(CLIENT_TAG, CONNECTION_MODE_NAME,
			        connectionMode.toString());
			props.setIntProperty(CLIENT_TAG, HTTP_REQUEST_TIMEOUT_NAME,
			        httpRequestTimeout);

			props.setBoolProperty(CLIENT_TAG, SIMPLE_URL_ENABLE_NAME,
			        simpleURLEnable);
			props.setProperty(CLIENT_TAG, COMPRESSOR_NAME,
			        compressor.toString());
			props.setProperty(CLIENT_TAG, ENCRYPTER_NAME, encrypter.toString());
			props.setProperty(CLIENT_TAG, USER_AGENT_NAME, httpProxyUserAgent);
			for (AppIdBinding binding : appIdBindings)
			{
				binding.putToIniProperties(props);
			}
			props.store(fos);
		}
		catch (Exception e)
		{
			throw e;
		}
	}

	public ProxyInfo getLocalProxy()
	{
		return DesktopFrameworkConfiguration.getInstance().getLocalProxy();
	}

	@Override
	public void reload()
	{
		loadConfig();
	}

	@Override
	public File getConfigurationFile()
	{
		return getConfigFile();
	}
}
