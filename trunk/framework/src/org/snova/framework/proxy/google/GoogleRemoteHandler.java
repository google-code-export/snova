/**
 * 
 */
package org.snova.framework.proxy.google;

import java.net.InetSocketAddress;
import java.net.URL;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.arch.config.IniProperties;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.Connector;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;

/**
 * @author wqy
 * 
 */
public class GoogleRemoteHandler implements RemoteProxyHandler
{
	protected static Logger	  logger	= LoggerFactory
	                                         .getLogger(GoogleRemoteHandler.class);
	private static HttpClient	httpsClient;
	private static HttpClient	httpClient;
	
	private static void initHttpClient() throws Exception
	{
		if (null != httpsClient)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		Options options = new Options();
		options.maxIdleConnsPerHost = cfg.getIntProperty("Google",
		        "ConnectionPoolSize", 20);
		String proxy = cfg.getProperty("Google", "Proxy");
		if (null != proxy)
		{
			final URL proxyUrl = new URL(proxy);
			options.proxyCB = new ProxyCallback()
			{
				@Override
				public URL getProxy(HttpRequest request)
				{
					return proxyUrl;
				}
			};
			options.connector = new Connector()
			{
				@Override
				public ChannelFuture connect(String host, int port)
				{
					String remoteHost = HostsService.getMappingHost(host);
					ChannelFuture future = SharedObjectHelper
					        .getClientBootstrap().connect(
					                new InetSocketAddress(remoteHost, port));
					return future;
				}
			};
		}
		httpsClient = new HttpClient(options,
		        SharedObjectHelper.getClientBootstrap());
	}
	
	public GoogleRemoteHandler()
	{
		try
		{
			initHttpClient();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void handleRequest(final LocalProxyHandler local, HttpRequest req)
	{
		if (req.getMethod().equals(HttpMethod.CONNECT))
		{
			
		}
		else
		{
			if (!req.getUri().startsWith("http://"))
			{
				req.setUri("http://" + HttpHeaders.getHost(req) + req.getUri());
			}
			try
			{
				httpsClient.execute(req,
				        new FutureCallback.FutureCallbackAdapter()
				        {
					        @Override
					        public void onResponse(HttpResponse res)
					        {
						        local.handleResponse(GoogleRemoteHandler.this,
						                res);
					        }
					        
					        @Override
					        public void onError(String error)
					        {
						        GoogleRemoteHandler.this.close();
						        local.close();
					        }
					        
					        @Override
					        public void onBody(HttpChunk chunk)
					        {
						        local.handleChunk(GoogleRemoteHandler.this,
						                chunk);
						        
					        }
				        });
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			
		}
	}
	
	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
	}
	
	@Override
	public void handleRawData(LocalProxyHandler local, ChannelBuffer raw)
	{
		
	}
	
	@Override
	public void close()
	{
	}
	
}
