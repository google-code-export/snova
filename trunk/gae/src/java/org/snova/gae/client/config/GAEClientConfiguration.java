/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Config.java 
 *
 * @author yinqiwen [ 2010-5-14 | 08:49:33 PM]
 *
 */
package org.snova.gae.client.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.SSLSocketFactory;

import org.arch.config.IniProperties;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptType;
import org.arch.util.StringHelper;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.DesktopFrameworkConfiguration;
import org.snova.framework.config.ReloadableConfiguration;
import org.snova.framework.config.ReloadableConfigurationMonitor;
import org.snova.framework.util.proxy.ProxyInfo;
import org.snova.framework.util.proxy.ProxyType;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.GAEPluginVersion;

/**
 *
 */
public class GAEClientConfiguration implements ReloadableConfiguration
{
	protected static Logger logger = LoggerFactory
	        .getLogger(GAEClientConfiguration.class);
	private static GAEClientConfiguration instance = new GAEClientConfiguration();

	private static File getConfigFile()
	{
		URL url = GAEClientConfiguration.class.getResource("/"
		        + GAEConstants.CLIENT_CONF_NAME);
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

	private GAEClientConfiguration()
	{
		loadConfig();
		ReloadableConfigurationMonitor.getInstance().registerConfigFile(this);
	}

	private static final String GAE_TAG = "GAE";
	private static final String MASTER_NODE_NAME = "MasterNode";
	private static final String WORKER_NODE_NAME = "WorkerNode";
	private static final String XMPP_TAG = "XMPP";
	private static final String ACCOUNT_NAME = "Account";
	private static final String CLIENT_TAG = "Client";
	private static final String CONNECTION_MODE_NAME = "ConnectionMode";
	private static final String SESSION_TIMEOUT_NAME = "SessionTimeOut";
	private static final String SIMPLE_URL_ENABLE_NAME = "SimpleURLEnable";
	private static final String COMPRESSOR_NAME = "Compressor";
	private static final String ENCRYPTER_NAME = "Encrypter";
	private static final String CONN_POOL_SIZE_NAME = "ConnectionPoolSize";
	private static final String FETCH_LIMIT_NAME = "FetchLimitSize";
	private static final String CONCURRENT_RANGE_FETCHER_NAME = "ConcurrentRangeFetchWorker";
	private static final String RANGE_RETRY_LIMIT_NAME = "RangeFetchRetryLimit";
	private static final String USER_AGENT_NAME = "UserAgent";
	private static final String MATCH_SITES_NAME = "Sites";
	private static final String MATCH_END_URLS_NAME = "EndURLs";

	private static final String INJECT_RANGE_TAG = "InjectRange";
	private static final String APPID_BINDING_TAG = "AppIdBinding";
	private static final String GOOGLE_PROXY_TAG = "GoogleProxy";
	private static final String MODE_NAME = "Mode";
	private static final String PROXY_NAME = "Proxy";

	private void loadConfig()
	{
		try
		{
			FileInputStream fis = new FileInputStream(getConfigFile());
			IniProperties props = new IniProperties();
			props.load(fis);
			Properties ps = props.getProperties(GAE_TAG);
			if (null != ps)
			{
				for (Object key : ps.keySet())
				{
					String k = ((String) key).trim();
					if (k.equals(MASTER_NODE_NAME))
					{
						masterNode.parse(ps.getProperty(k));
					}
					else if (k.startsWith(WORKER_NODE_NAME))
					{
						GAEServerAuth auth = new GAEServerAuth();
						if (!auth.parse(ps.getProperty(k)))
						{
							throw new Exception("Failed to parse line:" + k
							        + "=" + ps.getProperty(k));
						}
						serverAuths.add(auth);
					}
				}

			}

			List<XmppAccount> tmp = new LinkedList<GAEClientConfiguration.XmppAccount>();
			ps = props.getProperties(XMPP_TAG);
			if (null != ps)
			{
				for (Object key : ps.keySet())
				{
					String k = ((String) key).trim();
					if (k.startsWith(ACCOUNT_NAME))
					{
						XmppAccount accont = new XmppAccount();
						if (!accont.parse(ps.getProperty(k)))
						{
							throw new Exception("Failed to parse line:" + k
							        + "=" + ps.getProperty(k));
						}
						tmp.add(accont);
					}
				}
				xmppAccounts = tmp;
			}

			connectionMode = ConnectionMode.valueOf(props.getProperty(
			        CLIENT_TAG, CONNECTION_MODE_NAME, "HTTP"));
			sessionTimeout = props.getIntProperty(CLIENT_TAG,
			        SESSION_TIMEOUT_NAME, sessionTimeout);
			simpleURLEnable = props.getBoolProperty(CLIENT_TAG,
			        SIMPLE_URL_ENABLE_NAME, false);
			compressor = CompressorType.valueOf(props.getProperty(CLIENT_TAG,
			        COMPRESSOR_NAME, "Snappy").toUpperCase());
			encrypter = EncryptType.valueOf(props.getProperty(CLIENT_TAG,
			        ENCRYPTER_NAME, "SE1"));
			connectionPoolSize = props.getIntProperty(CLIENT_TAG,
			        CONN_POOL_SIZE_NAME, 7);
			fetchLimitSize = props.getIntProperty(CLIENT_TAG, FETCH_LIMIT_NAME,
			        fetchLimitSize);
			concurrentFetchWorker = props.getIntProperty(CLIENT_TAG,
			        CONCURRENT_RANGE_FETCHER_NAME, 3);
			rangeFetchRetryLimit = props.getIntProperty(CLIENT_TAG,
			        RANGE_RETRY_LIMIT_NAME, 1);
			httpProxyUserAgent = props.getProperty(CLIENT_TAG, USER_AGENT_NAME,
			        "Snova-GAE V" + GAEPluginVersion.value);

			ps = props.getProperties(INJECT_RANGE_TAG);
			if (null != ps)
			{
				String k = ps.getProperty(MATCH_SITES_NAME);
				rangeMatcher.parseSites(k);
				k = ps.getProperty(MATCH_END_URLS_NAME);
				rangeMatcher.parseEndURLs(k);
			}

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

			int modevalue = props.getIntProperty(GOOGLE_PROXY_TAG, MODE_NAME,
			        GoolgeProxyMode.OVERRIDE.value);
			gProxymode = GoolgeProxyMode.fromInt(modevalue);
			String s = props.getProperty(GOOGLE_PROXY_TAG, PROXY_NAME);
			if (null != s)
			{
				googleProxy = new ProxyInfo();
				if (!googleProxy.parse(s))
				{
					googleProxy = null;
					logger.error("Failed to parse Google Proxy for gae plugin.");
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
		HTTP, HTTPS, XMPP;
	}

	public static class GAEServerAuth
	{
		public String appid;
		public String user;
		public String passwd;
		public boolean backendEnable;

		public void init()
		{
			if (user == null || user.equals(""))
			{
				user = GAEConstants.ANONYMOUSE_NAME;
			}
			if (passwd == null || passwd.equals(""))
			{
				passwd = GAEConstants.ANONYMOUSE_NAME;
			}
			appid = appid.trim();
			user = user.trim();
			passwd = passwd.trim();
		}

		public boolean parse(String line)
		{
			if (null == line || line.trim().isEmpty())
			{
				return false;
			}
			line = line.trim();
			String[] ss = StringHelper.split(line, '@');
			if (ss.length == 1)
			{
				appid = line;
			}
			else
			{
				appid = ss[ss.length - 1];
				int index = line.indexOf("@" + appid);
				String userpass = line.substring(0, index);
				String[] ks = StringHelper.split(userpass, ':');
				user = ks[0];
				passwd = ks[1];
			}
			if (appid.indexOf("/backend") != -1)
			{
				appid = appid.substring(0, appid.indexOf("/backend"));
				backendEnable = true;
			}
			init();
			return true;
		}

		public String toString()
		{
			return user + ":" + passwd + "@" + appid
			        + (backendEnable ? "/backend" : "");
		}
	}

	public static class MasterNode
	{
		public String appid = "snova-master";
		public boolean backendEnable = false;

		public boolean parse(String line)
		{
			if (null == line || line.trim().isEmpty())
			{
				return false;
			}
			line = line.trim();
			appid = line;
			if (appid.indexOf("/backend") != -1)
			{
				appid = appid.substring(0, appid.indexOf("/backend"));
				backendEnable = true;
			}
			return true;
		}

		public String toString()
		{
			return appid + (backendEnable ? "/backend" : "");
		}
	}

	public static class XmppAccount
	{
		private static final String GTALK_SERVER = "talk.google.com";
		private static final String GTALK_SERVER_NAME = "gmail.com";
		private static final int GTALK_SERVER_PORT = 5222;

		private static final String OVI_SERVER = "chat.ovi.com";
		private static final String OVI_SERVER_NAME = "ovi.com";
		private static final int OVI_SERVER_PORT = 5223;

		protected static final int DEFAULT_PORT = 5222;

		public boolean parse(String line)
		{
			if (null == line || line.trim().isEmpty())
			{
				return false;
			}
			line = line.trim();
			String accountstr = line;
			String serverstr = null;
			if (line.indexOf('#') == -1)
			{
				logger.error("No XMPP account/server separator '#' found in gae-client.conf.");
				return false;
			}
			else
			{
				accountstr = line.substring(0, line.lastIndexOf('#'));
				serverstr = line.substring(line.lastIndexOf('#') + 1);
			}

			if (accountstr.indexOf(':') == -1)
			{
				logger.error("No XMPP user/pass separator ':' found in gae-client.conf.");
				return false;
			}
			jid = accountstr.substring(0, accountstr.lastIndexOf(':'));
			passwd = accountstr.substring(accountstr.lastIndexOf(':') + 1);
			if (null != serverstr)
			{
				serverHost = serverstr;
				if (line.indexOf(':') != -1)
				{
					serverHost = line.substring(0, line.indexOf(':'));
					String portstr = line.substring(line.indexOf(':') + 1);
					if (portstr.indexOf("/oldssl") != -1)
					{
						this.isOldSSLEnable = true;
						portstr = portstr.substring(0, portstr.indexOf('/'));
					}
					serverPort = Integer.parseInt(line.substring(line
					        .indexOf(':') + 1));
				}
			}
			return true;
		}

		public XmppAccount init()
		{
			String server = StringUtils.parseServer(jid).trim();
			// String name = null;
			if (server.equals(GTALK_SERVER_NAME))
			{
				if (null == this.serverHost || this.serverHost.isEmpty())
				{
					this.serverHost = GTALK_SERVER;
				}
				if (0 == this.serverPort)
				{
					this.serverPort = GTALK_SERVER_PORT;
				}
				this.name = jid;
			}
			else if (server.equals(OVI_SERVER_NAME))
			{
				if (null == this.serverHost || this.serverHost.isEmpty())
				{
					this.serverHost = OVI_SERVER;
				}
				if (0 == this.serverPort)
				{
					this.serverPort = OVI_SERVER_PORT;
				}
				this.name = StringUtils.parseName(jid);
				this.isOldSSLEnable = true;
			}
			else
			{
				if (null == this.serverHost || this.serverHost.isEmpty())
				{
					this.serverHost = server;
				}
				if (0 == this.serverPort)
				{
					this.serverPort = DEFAULT_PORT;
				}
				this.name = StringUtils.parseName(jid);
			}
			String serviceName = server;
			connectionConfig = new ConnectionConfiguration(this.serverHost,
			        serverPort, serviceName);
			if (isOldSSLEnable)
			{
				connectionConfig
				        .setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
				connectionConfig
				        .setSocketFactory(SSLSocketFactory.getDefault());
			}

			return this;
		}

		public String jid;
		public String passwd;
		public int serverPort;
		public String serverHost;
		public boolean isOldSSLEnable;

		public ConnectionConfiguration connectionConfig;
		public String name;

		public String toString()
		{
			return jid + ":" + passwd + "#" + serverHost + ":" + serverPort
			        + (isOldSSLEnable ? "/oldssl" : "");
		}
	}

	private List<GAEServerAuth> serverAuths = new LinkedList<GAEServerAuth>();

	public void setGAEServerAuths(List<GAEServerAuth> serverAuths)
	{
		this.serverAuths = serverAuths;
	}

	public List<GAEServerAuth> getGAEServerAuths()
	{
		return serverAuths;
	}

	private MasterNode masterNode = new MasterNode();

	public MasterNode getMasterNode()
	{
		return masterNode;
	}

	public GAEServerAuth getGAEServerAuth(String appid)
	{
		for (GAEServerAuth auth : serverAuths)
		{
			if (auth.appid.equals(appid))
			{
				return auth;
			}
		}
		return null;
	}

	private List<XmppAccount> xmppAccounts = new LinkedList<GAEClientConfiguration.XmppAccount>();

	public void setXmppAccounts(List<XmppAccount> xmppAccounts)
	{
		this.xmppAccounts = xmppAccounts;
	}

	public List<XmppAccount> getXmppAccounts()
	{
		return xmppAccounts;
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

	private int sessionTimeout = 60000;

	public void setSessionTimeOut(int sessionTimeout)
	{
		this.sessionTimeout = sessionTimeout;
	}

	public int getSessionTimeOut()
	{
		return sessionTimeout;
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

	private int connectionPoolSize = 7;

	public void setConnectionPoolSize(int connectionPoolSize)
	{
		this.connectionPoolSize = connectionPoolSize;
	}

	public int getConnectionPoolSize()
	{
		return connectionPoolSize;
	}

	static class InjectRangeHeaderMatcher
	{
		List<String> injectRangeHeaderSiteSet = new LinkedList<String>();
		List<String> injectRangeHeaderURLSet = new LinkedList<String>();

		void putToIniProperties(IniProperties props)
		{
			StringBuilder buffer = new StringBuilder();
			for (String s : injectRangeHeaderSiteSet)
			{
				buffer.append(s).append("|");
			}
			String v = buffer.toString();
			if (!v.isEmpty())
			{
				props.setProperty(APPID_BINDING_TAG, MATCH_SITES_NAME,
				        buffer.toString());
			}

			buffer = new StringBuilder();
			for (String s : injectRangeHeaderURLSet)
			{
				buffer.append(s).append("|");
			}
			v = buffer.toString();
			if (!v.isEmpty())
			{
				props.setProperty(APPID_BINDING_TAG, MATCH_END_URLS_NAME,
				        buffer.toString());
			}

		}

		boolean parseSites(String line)
		{
			injectRangeHeaderSiteSet.clear();
			String[] ss = line.split("[,|;|\\|]");
			for (String k : ss)
			{
				if (!k.trim().isEmpty())
				{
					injectRangeHeaderSiteSet.add(k.trim());
				}
			}
			return true;
		}

		boolean parseEndURLs(String line)
		{
			injectRangeHeaderURLSet.clear();
			String[] ss = line.split("[,|;|\\|]");
			for (String k : ss)
			{
				if (!k.trim().isEmpty())
				{
					injectRangeHeaderURLSet.add(k.trim());
				}
			}
			return true;
		}

		boolean siteMatched(String host)
		{
			for (String site : injectRangeHeaderSiteSet)
			{
				if (!site.isEmpty() && host.indexOf(site) != -1)
				{
					return true;
				}
			}
			return false;
		}

		boolean endUrlMatched(String url)
		{
			for (String pattern : injectRangeHeaderURLSet)
			{
				if (!pattern.isEmpty() && url.endsWith(pattern))
				{
					return true;
				}
			}
			return false;
		}
	}

	private InjectRangeHeaderMatcher rangeMatcher = new InjectRangeHeaderMatcher();

	public boolean isInjectRangeHeaderSiteMatched(String host)
	{
		return rangeMatcher.siteMatched(host);
	}

	public boolean isInjectRangeHeaderURLMatched(String url)
	{
		return rangeMatcher.endUrlMatched(url);
	}

	private int fetchLimitSize = 512000;

	public void setFetchLimitSize(int fetchLimitSize)
	{
		this.fetchLimitSize = fetchLimitSize;
	}

	public int getFetchLimitSize()
	{
		return fetchLimitSize;
	}

	private int concurrentFetchWorker;

	public void setConcurrentRangeFetchWorker(int num)
	{
		this.concurrentFetchWorker = num;
	}

	public int getConcurrentRangeFetchWorker()
	{
		return concurrentFetchWorker;
	}

	private int rangeFetchRetryLimit = 1;

	public void setRangeFetchRetryLimit(int num)
	{
		this.rangeFetchRetryLimit = num;
	}

	public int getRangeFetchRetryLimit()
	{
		return rangeFetchRetryLimit;
	}

	private ProxyInfo googleProxy;

	public void setGoogleProxy(ProxyInfo localProxy)
	{
		this.googleProxy = localProxy;
	}

	public ProxyInfo getGoogleProxy()
	{
		return googleProxy;
	}

	public static enum GoolgeProxyMode
	{
		DISABLE(0), OVERRIDE(1), NEXT_CHAIN(2);
		int value;

		GoolgeProxyMode(int v)
		{
			this.value = v;
		}

		public int getValue()
		{
			return value;

		}

		public static GoolgeProxyMode fromInt(int v)
		{
			switch (v)
			{
				case 0:
					return DISABLE;
				case 1:
					return OVERRIDE;
				case 2:
					return NEXT_CHAIN;
				default:
					return OVERRIDE;
			}
		}
	}

	private GoolgeProxyMode gProxymode = GoolgeProxyMode.OVERRIDE;

	public void setGoolgeProxyMode(GoolgeProxyMode mode)
	{
		this.gProxymode = mode;
	}

	public GoolgeProxyMode getGoolgeProxyMode()
	{
		return gProxymode;
	}

	public void init() throws Exception
	{
		if (googleProxy != null)
		{
			if (null != googleProxy.host)
			{
				googleProxy.host = googleProxy.host.trim();
			}
			if (null == googleProxy.host || googleProxy.host.isEmpty())
			{
				googleProxy = null;
			}
			else if (googleProxy.port == 0)
			{
				if (googleProxy.type.equals(ProxyType.HTTP))
				{
					googleProxy.port = 80;
				}
				else if (googleProxy.type.equals(ProxyType.HTTPS))
				{
					googleProxy.port = 443;
				}
			}
		}
		if (null != serverAuths)
		{
			for (int i = 0; i < serverAuths.size(); i++)
			{
				GAEServerAuth auth = serverAuths.get(i);
				if (auth.appid == null || auth.appid.trim().isEmpty())
				{
					serverAuths.remove(i);
					i--;
					continue;
				}
				auth.init();
			}
		}

		if (getConnectionMode().equals(ConnectionMode.XMPP))
		{
			for (int i = 0; i < xmppAccounts.size(); i++)
			{
				XmppAccount account = xmppAccounts.get(i);
				if (account.jid == null || account.jid.isEmpty())
				{
					xmppAccounts.remove(i);
					i--;
				}
				else
				{
					account.init();
				}
			}
		}
		if (connectionMode.equals(ConnectionMode.XMPP)
		        && (null == xmppAccounts || xmppAccounts.isEmpty()))
		{
			throw new Exception("Since the connection mode is "
			        + ConnectionMode.XMPP
			        + ", at least one XMPP account needed.");
		}

		if (googleProxy == null || googleProxy.host.contains("google"))
		{
			simpleURLEnable = true;
		}

		if (null == masterNode || masterNode.appid == null)
		{
			boolean backendEnable = null != masterNode ? masterNode.backendEnable
			        : false;
			masterNode = new MasterNode();
			masterNode.appid = GAEConstants.SNOVA_MASTER_APPID;
			masterNode.backendEnable = backendEnable;
		}
	}

	private List<AppIdBinding> appIdBindings = new LinkedList<GAEClientConfiguration.AppIdBinding>();

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

	public String getBindingAppId(String host)
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

	public ProxyInfo getGoogleProxyChain()
	{
		if (null != googleProxy
		        && gProxymode.equals(GoolgeProxyMode.NEXT_CHAIN))
		{
			return googleProxy;
		}
		return null;
	}

	public ProxyInfo getLocalProxy()
	{
		if (null == googleProxy || gProxymode.equals(GoolgeProxyMode.DISABLE)
		        || gProxymode.equals(GoolgeProxyMode.NEXT_CHAIN))
		{
			return DesktopFrameworkConfiguration.getInstance().getLocalProxy();
		}
		switch (gProxymode)
		{
			case OVERRIDE:
			{
				return googleProxy;
			}
			default:
			{
				return null;
			}
		}
	}

	public static GAEClientConfiguration getInstance()
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
			for (GAEServerAuth auth : serverAuths)
			{
				props.setProperty(GAE_TAG, WORKER_NODE_NAME + "[" + i + "]",
				        auth.toString());
				i++;
			}
			props.setProperty(GAE_TAG, MASTER_NODE_NAME, masterNode.toString());

			i = 0;
			for (XmppAccount account : xmppAccounts)
			{
				props.setProperty(XMPP_TAG, ACCOUNT_NAME + "[" + i + "]",
				        account.toString());
				i++;
			}

			props.setProperty(CLIENT_TAG, CONNECTION_MODE_NAME,
			        connectionMode.toString());
			props.setIntProperty(CLIENT_TAG, SESSION_TIMEOUT_NAME,
			        sessionTimeout);
			props.setIntProperty(CLIENT_TAG, CONN_POOL_SIZE_NAME,
			        connectionPoolSize);
			props.setIntProperty(CLIENT_TAG, RANGE_RETRY_LIMIT_NAME,
			        rangeFetchRetryLimit);
			props.setIntProperty(CLIENT_TAG, CONCURRENT_RANGE_FETCHER_NAME,
			        concurrentFetchWorker);
			props.setIntProperty(CLIENT_TAG, FETCH_LIMIT_NAME, fetchLimitSize);
			props.setBoolProperty(CLIENT_TAG, SIMPLE_URL_ENABLE_NAME,
			        simpleURLEnable);
			props.setProperty(CLIENT_TAG, COMPRESSOR_NAME,
			        compressor.toString());
			props.setProperty(CLIENT_TAG, ENCRYPTER_NAME, encrypter.toString());
			props.setProperty(CLIENT_TAG, USER_AGENT_NAME, httpProxyUserAgent);

			if (null != googleProxy)
			{
				props.setIntProperty(GOOGLE_PROXY_TAG, MODE_NAME,
				        gProxymode.value);
				props.setProperty(GOOGLE_PROXY_TAG, PROXY_NAME,
				        googleProxy.toString());
			}

			for (AppIdBinding binding : appIdBindings)
			{
				binding.putToIniProperties(props);
			}

			rangeMatcher.putToIniProperties(props);
			props.store(fos);
		}
		catch (Exception e)
		{
			throw e;
		}
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
