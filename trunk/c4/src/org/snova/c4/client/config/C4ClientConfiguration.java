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
	private static final String RSERVER_TAG = "RServer";
	private static final String CONNECTION_MODE_NAME = "ConnectionMode";
	private static final String SESSION_IDLE_TIMEOUT_NAME = "SessionIdleTimeout";
	private static final String SIMPLE_URL_ENABLE_NAME = "SimpleURLEnable";
	private static final String COMPRESSOR_NAME = "Compressor";
	private static final String ENCRYPTER_NAME = "Encrypter";
	private static final String HEARTBEAT_PERIOD_NAME = "HeartBeatPeriod";
	private static final String HTTP_REQUEST_TIMEOUT_NAME = "HTTPRequestTimeout";
	private static final String PULL_TRANSACTION_NAME = "PullTransactionTime";
	private static final String CLIENT_PULL_ENABLE_NAME = "ClientPullEnable";
	private static final String SERVER_PULL_ENABLE_NAME = "ServerPullEnable";
	private static final String USER_AGENT_NAME = "UserAgent";
	private static final String DUAL_CONN_ENABLE_NAME = "DualConnectionEnable";
	private static final String MIN_WRITE_PERIOD = "MinWritePeriod";
	private static final String CONN_POOL_SIZE_NAME = "ConnectionPoolSize";
	private static final String RSERVER_PORT_NAME = "Port";
	private static final String RSERVER_EXTERNAL_IP_NAME = "ExternalIP";

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
			sessionTimeout = props.getIntProperty(CLIENT_TAG,
			        SESSION_IDLE_TIMEOUT_NAME, sessionTimeout);
			simpleURLEnable = props.getBoolProperty(CLIENT_TAG,
			        SIMPLE_URL_ENABLE_NAME, false);
			compressor = CompressorType.valueOf(props.getProperty(CLIENT_TAG,
			        COMPRESSOR_NAME, "Snappy").toUpperCase());
			encrypter = EncryptType.valueOf(props.getProperty(CLIENT_TAG,
			        ENCRYPTER_NAME, "SE1"));
			sessionTimeout = props.getIntProperty(CLIENT_TAG,
			        SESSION_IDLE_TIMEOUT_NAME, sessionTimeout);
			pullTransactionTime = props.getIntProperty(CLIENT_TAG,
			        PULL_TRANSACTION_NAME, pullTransactionTime);
			heartBeatPeriod = props.getIntProperty(CLIENT_TAG,
			        HEARTBEAT_PERIOD_NAME, 2000);
			httpRequestTimeout = props.getIntProperty(CLIENT_TAG,
			        HTTP_REQUEST_TIMEOUT_NAME, 30000);
			clientPullEnable = props.getBoolProperty(CLIENT_TAG,
			        CLIENT_PULL_ENABLE_NAME, true);
			serverPullEnable = props.getBoolProperty(CLIENT_TAG,
			        SERVER_PULL_ENABLE_NAME, true);
			dualConnectionEnable = props.getBoolProperty(CLIENT_TAG,
			        DUAL_CONN_ENABLE_NAME, true);
			minWritePeriod = props.getIntProperty(CLIENT_TAG, MIN_WRITE_PERIOD,
			        500);
			connectionPoolSize = props.getIntProperty(CLIENT_TAG,
			        CONN_POOL_SIZE_NAME, 2);
			rServerPort = props.getIntProperty(RSERVER_TAG, RSERVER_PORT_NAME, rServerPort);
			httpProxyUserAgent = props.getProperty(CLIENT_TAG, USER_AGENT_NAME,
			        "Snova-C4 V" + C4PluginVersion.value);
			externalIP = props.getProperty(CLIENT_TAG, RSERVER_EXTERNAL_IP_NAME);

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

	private int connectionPoolSize = 1;

	public int getConnectionPoolSize()
	{
		return connectionPoolSize;
	}

	public void setConnectionPoolSize(int size)
	{
		connectionPoolSize = size;
	}

	private int sessionTimeout = 50000;

	public void setSessionIdleTimeout(int sessionTimeout)
	{
		this.sessionTimeout = sessionTimeout;
	}

	public int getSessionIdleTimeout()
	{
		return sessionTimeout;
	}

	private int minWritePeriod = 500;

	public int getMinWritePeriod()
	{
		return minWritePeriod;
	}

	public void setMinWritePeriod(int v)
	{
		minWritePeriod = v;
	}

	private boolean clientPullEnable = true;

	public void setClientPullEnable(boolean v)
	{
		this.clientPullEnable = v;
	}

	public boolean isClientPullEnable()
	{
		return clientPullEnable;
	}

	private boolean serverPullEnable = true;

	public void setServerPullEnable(boolean v)
	{
		this.serverPullEnable = v;
	}

	public boolean isServerPullEnable()
	{
		return serverPullEnable;
	}

	private boolean dualConnectionEnable = true;

	public void setDualConnectionEnable(boolean v)
	{
		this.dualConnectionEnable = v;
	}

	public boolean isDualConnectionEnable()
	{
		return dualConnectionEnable;
	}

	private int pullTransactionTime = 25000;

	public void setPullTransactionTime(int v)
	{
		this.pullTransactionTime = v;
	}

	public int getPullTransactionTime()
	{
		return pullTransactionTime;
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

	private int heartBeatPeriod = 2000;

	public void setheartBeatPeriod(int heartBeatPeriod)
	{
		this.heartBeatPeriod = heartBeatPeriod;
	}

	public int getHeartBeatPeriod()
	{
		return heartBeatPeriod;
	}

	private int httpRequestTimeout = 30000;

	public void setHTTPRequestTimeout(int timeout)
	{
		this.httpRequestTimeout = timeout;
	}

	public int getHTTPRequestTimeout()
	{
		return httpRequestTimeout;
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
	
	private String externalIP;

	public String getExternalIP()
	{
		return externalIP;
	}

	public void setExternalIP(String ip)
	{
		externalIP = ip;
	}
	
	private int rServerPort = 48101;
	public int getRServerPort()
	{
		return rServerPort;
	}
	public void setRServerPort(int port)
	{
		rServerPort = port;
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
			props.setIntProperty(CLIENT_TAG, SESSION_IDLE_TIMEOUT_NAME,
			        sessionTimeout);
			props.setIntProperty(CLIENT_TAG, HTTP_REQUEST_TIMEOUT_NAME,
			        httpRequestTimeout);
			props.setIntProperty(CLIENT_TAG, PULL_TRANSACTION_NAME,
			        pullTransactionTime);
			props.setIntProperty(CLIENT_TAG, HEARTBEAT_PERIOD_NAME,
			        heartBeatPeriod);
			props.setIntProperty(CLIENT_TAG, CONN_POOL_SIZE_NAME,
			        connectionPoolSize);
			props.setBoolProperty(CLIENT_TAG, CLIENT_PULL_ENABLE_NAME,
			        clientPullEnable);
			props.setBoolProperty(CLIENT_TAG, SERVER_PULL_ENABLE_NAME,
			        serverPullEnable);
			props.setBoolProperty(CLIENT_TAG, DUAL_CONN_ENABLE_NAME,
			        dualConnectionEnable);
			props.setBoolProperty(CLIENT_TAG, SIMPLE_URL_ENABLE_NAME,
			        simpleURLEnable);
			props.setProperty(CLIENT_TAG, COMPRESSOR_NAME,
			        compressor.toString());
			props.setProperty(CLIENT_TAG, ENCRYPTER_NAME, encrypter.toString());
			props.setProperty(CLIENT_TAG, USER_AGENT_NAME, httpProxyUserAgent);
			props.setIntProperty(CLIENT_TAG, MIN_WRITE_PERIOD, minWritePeriod);
			props.setIntProperty(RSERVER_TAG, RSERVER_PORT_NAME, rServerPort);
			if(null != externalIP)
			{
				props.setProperty(RSERVER_TAG, RSERVER_EXTERNAL_IP_NAME, externalIP);
			}
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
