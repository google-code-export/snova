/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Config.java 
 *
 * @author yinqiwen [ 2010-5-14 | 08:49:33 PM]
 *
 */
package org.snova.heroku.client.config;

import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.LinkedList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;

/**
 *
 */
@XmlRootElement(name = "Configure")
public class HerokuClientConfiguration
{
	protected static Logger	                 logger	     = LoggerFactory
	                                                             .getLogger(HerokuClientConfiguration.class);
	private static HerokuClientConfiguration	instance	= null;
	
	static
	{
		try
		{
			JAXBContext context = JAXBContext
			        .newInstance(HerokuClientConfiguration.class);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			instance = (HerokuClientConfiguration) unmarshaller
			        .unmarshal(HerokuClientConfiguration.class
			                .getResource("/heroku-client.xml"));
			instance.init();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			logger.error("Failed to load gae-client config file!", e);
		}
	}
	
	public static enum ConnectionMode
	{
		HTTP, RSOCKET, AUTO;
	}
	
	public static class HerokuServerAuth
	{
		@XmlAttribute
		public String	domain;
		
		@XmlAttribute
		public int	port = 80;
		@XmlAttribute
		public String	user;
		@XmlAttribute
		public String	passwd;
		
		public void init()
		{
			domain = domain.trim();
			//user = user.trim();
			//passwd = passwd.trim();
		}
	}
	
	public static enum ProxyType
	{
		HTTP("http"), HTTPS("https");
		String	value;
		
		ProxyType(String v)
		{
			value = v;
			
		}
		
		public static ProxyType fromStr(String str)
		{
			if (str.equalsIgnoreCase("http"))
			{
				return HTTP;
			}
			if (str.equalsIgnoreCase("https"))
			{
				return HTTPS;
			}
			return HTTP;
		}
	}
	
	public static class ProxyInfo
	{
		@XmlAttribute
		public String		host;
		@XmlAttribute
		public int		 port		= 0;
		@XmlAttribute
		public String		user;
		@XmlAttribute
		public String		passwd;
		
		@XmlAttribute
		public ProxyType	type	= ProxyType.HTTP;
		
	}
	
	@XmlElements(@XmlElement(name = "WorkerNode"))
	private List<HerokuServerAuth>	serverAuths	= new LinkedList<HerokuServerAuth>();
	
	@XmlTransient
	public void setHerokuServerAuths(List<HerokuServerAuth> serverAuths)
	{
		this.serverAuths = serverAuths;
	}
	
	public List<HerokuServerAuth> getHerokuServerAuths()
	{
		return serverAuths;
	}
	
	
	public HerokuServerAuth getHerokuServerAuth(String domain)
	{
		for (HerokuServerAuth auth : serverAuths)
		{
			if (auth.domain.equals(domain))
			{
				return auth;
			}
		}
		return null;
	}
	
	
	private String	connectionMode;
	
	@XmlElement(name = "ConnectionMode")
	public void setConnectionMode(String mode)
	{
		connectionMode = mode;
	}
	
	String getConnectionMode()
	{
		return connectionMode;
	}
	
	public ConnectionMode getConnectionModeType()
	{
		return ConnectionMode.valueOf(connectionMode.toUpperCase());
	}
	
	@XmlElement(name = "RSocketServer")
	private SimpleSocketAddress localProxyServerAddress = new SimpleSocketAddress(
	        "0.0.0.0", 48100);
	
	public SimpleSocketAddress getRSocketServerAddress()
	{
		return localProxyServerAddress;
	}
	
	private int	heartBeatPeriod;
	
	@XmlElement(name = "HeartBeatPeriod")
	public void setheartBeatPeriod(int heartBeatPeriod)
	{
		this.heartBeatPeriod = heartBeatPeriod;
	}
	
	public int getHeartBeatPeriod()
	{
		return heartBeatPeriod;
	}
	
	private String	compressor;
	
	@XmlElement(name = "Compressor")
	void setCompressor(String compressor)
	{
		this.compressor = compressor;
	}
	
	String getCompressor()
	{
		return compressor;
	}
	
	public CompressorType getCompressorType()
	{
		return CompressorType.valueOf(compressor.toUpperCase());
	}
	
	@XmlTransient
	public void setCompressorType(CompressorType type)
	{
		compressor = type.toString();
	}
	
	private String	encrypter;
	
	String getEncrypter()
	{
		return encrypter;
	}
	
	@XmlElement(name = "Encrypter")
	void setEncrypter(String httpUpStreamEncrypter)
	{
		this.encrypter = httpUpStreamEncrypter;
	}
	
	public EncryptType getEncrypterType()
	{
		return EncryptType.valueOf(encrypter.toUpperCase());
	}
	
	@XmlTransient
	public void setEncrypterType(EncryptType type)
	{
		this.encrypter = type.toString();
	}
	
	private boolean	simpleURLEnable;
	
	@XmlElement(name = "SimpleURLEnable")
	public void setSimpleURLEnable(boolean simpleURLEnable)
	{
		this.simpleURLEnable = simpleURLEnable;
	}
	
	public boolean isSimpleURLEnable()
	{
		return simpleURLEnable;
	}
	
	private int	connectionPoolSize;
	
	@XmlElement(name = "ConnectionPoolSize")
	public void setConnectionPoolSize(int connectionPoolSize)
	{
		this.connectionPoolSize = connectionPoolSize;
	}
	
	public int getConnectionPoolSize()
	{
		return connectionPoolSize;
	}
	
	// private String injectRangeHeaderSites;
	// private List<String> injectRangeHeaderSiteSet = new LinkedList<String>();
	
	static class InjectRangeHeaderMatcher
	{
		@XmlElements(@XmlElement(name = "Site"))
		List<String>	sites;
		@XmlElements(@XmlElement(name = "URL"))
		List<String>	urls;
		
		@XmlTransient
		List<String>	injectRangeHeaderSiteSet	= new LinkedList<String>();
		@XmlTransient
		List<String>	injectRangeHeaderURLSet		= new LinkedList<String>();
		
		void init()
		{
			if (null != sites)
			{
				for (String s : sites)
				{
					String[] ss = s.split("[,|;|\\|]");
					for (String k : ss)
					{
						if (!k.trim().isEmpty())
						{
							injectRangeHeaderSiteSet.add(k.trim());
						}
					}
				}
			}
			if (null != urls)
			{
				for (String s : urls)
				{
					String[] ss = s.split("[,|;|\\|]");
					for (String k : ss)
					{
						if (!k.trim().isEmpty())
						{
							injectRangeHeaderURLSet.add(k.trim());
						}
					}
				}
			}
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
		
		boolean urlMatched(String url)
		{
			for (String site : injectRangeHeaderURLSet)
			{
				if (!site.isEmpty() && url.indexOf(site) != -1)
				{
					return true;
				}
			}
			return false;
		}
	}
	
	@XmlElement(name = "InjectRangeHeader")
	InjectRangeHeaderMatcher	rangeMatcher;
	
	
	public boolean isInjectRangeHeaderSiteMatched(String host)
	{
		return rangeMatcher.siteMatched(host);
	}
	
	public boolean isInjectRangeHeaderURLMatched(String url)
	{
		return rangeMatcher.urlMatched(url);
	}
	
	private int	fetchLimitSize;
	
	@XmlElement(name = "FetchLimitSize")
	public void setFetchLimitSize(int fetchLimitSize)
	{
		this.fetchLimitSize = fetchLimitSize;
	}
	
	public int getFetchLimitSize()
	{
		return fetchLimitSize;
	}
	
	private int	concurrentFetchWorker;
	
	@XmlElement(name = "ConcurrentRangeFetchWorker")
	public void setConcurrentRangeFetchWorker(int num)
	{
		this.concurrentFetchWorker = num;
	}
	
	public int getConcurrentRangeFetchWorker()
	{
		return concurrentFetchWorker;
	}
	
	private ProxyInfo	localProxy;
	
	@XmlElement(name = "LocalProxy")
	public void setLocalProxy(ProxyInfo localProxy)
	{
		this.localProxy = localProxy;
	}
	
	public ProxyInfo getLocalProxy()
	{
		return localProxy;
	}
	
	public void init() throws Exception
	{
		if (localProxy != null)
		{
			if (null != localProxy.host)
			{
				localProxy.host = localProxy.host.trim();
			}
			if (null == localProxy.host || localProxy.host.isEmpty())
			{
				localProxy = null;
			}
			else if (localProxy.port == 0)
			{
				if (localProxy.type.equals(ProxyType.HTTP))
				{
					localProxy.port = 80;
				}
				else if (localProxy.type.equals(ProxyType.HTTPS))
				{
					localProxy.port = 443;
				}
			}
		}
		if (null != serverAuths)
		{
			for (int i = 0; i < serverAuths.size(); i++)
			{
				HerokuServerAuth auth = serverAuths.get(i);
				if (auth.domain == null || auth.domain.trim().isEmpty())
				{
					serverAuths.remove(i);
					i--;
					continue;
				}
				auth.init();
			}
		}
		
		
		if (localProxy == null || localProxy.host.contains("google"))
		{
			simpleURLEnable = true;
		}
		
		rangeMatcher.init();
	}


	@XmlElementWrapper(name = "DomainBindings")
	@XmlElements(@XmlElement(name = "Binding"))
	private List<DomainBinding>	appIdBindings;
	
	static class DomainBinding
	{
		@XmlAttribute
		String		 appid;
		@XmlElements(@XmlElement(name = "site"))
		List<String>	sites;
	}
	
	public String getBindingAppId(String host)
	{
		if (null != appIdBindings)
		{
			for (DomainBinding binding : appIdBindings)
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
	
	private String	httpProxyUserAgent;
	
	public String getUserAgent()
	{
		return httpProxyUserAgent;
	}
	
	@XmlElement(name = "UserAgent")
	public void setUserAgent(String v)
	{
		httpProxyUserAgent = v;
	}
	
	public static HerokuClientConfiguration getInstance()
	{
		return instance;
	}
	
	public void save() throws Exception
	{
		try
		{
			init();
			JAXBContext context = JAXBContext
			        .newInstance(HerokuClientConfiguration.class);
			Marshaller marshaller = context.createMarshaller();
			marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
			URL url = HerokuClientConfiguration.class.getResource("/heroku-client");
			String conf = URLDecoder.decode(url.getFile(), "UTF-8");
			FileOutputStream fos = new FileOutputStream(conf);
			// fos.write("<!-- This is generated by hyk-proxy-client GUI, it's not the orignal conf file -->\r\n".getBytes());
			marshaller.marshal(this, fos);
			fos.close();
		}
		catch (Exception e)
		{
			throw e;
		}
	}
}
