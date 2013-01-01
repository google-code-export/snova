/**
 * 
 */
package org.snova.framework.proxy.google;

import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.arch.config.IniProperties;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.server.ProxyHandler;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.Connector;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientHandler;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;

/**
 * @author wqy
 * 
 */
public class GoogleRemoteHandler implements RemoteProxyHandler
{
	protected static Logger logger = LoggerFactory
	        .getLogger(GoogleRemoteHandler.class);
	private static HttpClient httpsClient;
	private static HttpClient httpClient;

	private boolean useHttps;
	private HttpClientHandler proxyClientHandler;
	private ChannelFuture proxyTunnel;
	private LocalProxyHandler localHandler;
	private int failedCount;

	private static void initHttpClient() throws Exception
	{
		if (null != httpsClient)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		Options httpsOptions = new Options();
		Options httpOptions = new Options();
		httpsOptions.maxIdleConnsPerHost = cfg.getIntProperty("Google",
		        "ConnectionPoolSize", 20);
		httpOptions.maxIdleConnsPerHost = cfg.getIntProperty("Google",
		        "ConnectionPoolSize", 20);
		String proxy = cfg.getProperty("Google", "Proxy");
		if (null != proxy)
		{
			final URL proxyUrl = new URL(proxy);
			httpsOptions.proxyCB = new ProxyCallback()
			{
				public URL getProxy(HttpRequest request)
				{
					return proxyUrl;
				}
			};
			httpOptions = httpsOptions;
		}
		else
		{
			final URL fakeProxyUrl = new URL("http://127.0.0.1:48100");
			httpsOptions.proxyCB = new ProxyCallback()
			{
				public URL getProxy(HttpRequest request)
				{
					return fakeProxyUrl;
				}
			};
			httpsOptions.connector = new Connector()
			{
				public ChannelFuture connect(String host, int port)
				{
					String remoteHost = HostsService
					        .getMappingHost(GoogleConfig.googleHttpsHostAlias);
					ChannelFuture future = SharedObjectHelper
					        .getClientBootstrap().connect(
					                new InetSocketAddress(remoteHost, 443));
					SSLContext sslContext = null;
					try
					{
						sslContext = SSLContext.getDefault();
					}
					catch (NoSuchAlgorithmException e)
					{
						logger.error("", e);
					}
					SSLEngine sslEngine = sslContext.createSSLEngine();
					sslEngine.setUseClientMode(true);
					future.getChannel().getPipeline()
					        .addLast("ssl", new SslHandler(sslEngine));
					return future;
				}
			};
			httpOptions.connector = new Connector()
			{
				public ChannelFuture connect(String host, int port)
				{
					String remoteHost = HostsService
					        .getMappingHost(GoogleConfig.googleHttpHostAlias);
					ChannelFuture future = SharedObjectHelper
					        .getClientBootstrap().connect(
					                new InetSocketAddress(remoteHost, port));
					return future;
				}
			};
			httpOptions.proxyCB = httpsOptions.proxyCB;
		}
		httpsClient = new HttpClient(httpsOptions,
		        SharedObjectHelper.getClientBootstrap());
		httpClient = new HttpClient(httpOptions,
		        SharedObjectHelper.getClientBootstrap());
	}

	public GoogleRemoteHandler(String[] attr)
	{
		try
		{
			initHttpClient();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		if (null != attr)
		{
			for (String s : attr)
			{
				if (s.equalsIgnoreCase("https"))
				{
					useHttps = true;
				}
			}
		}
	}

	@Override
	public void handleRequest(final LocalProxyHandler local,
	        final HttpRequest req)
	{
		localHandler = local;
		if (req.getMethod().equals(HttpMethod.CONNECT))
		{
			ProxyHandler p = (ProxyHandler) localHandler;
			p.switchRawHandler();
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			String proxy = cfg.getProperty("Google", "Proxy");
			if (null != proxy)
			{
				try
				{
					URL proxyUrl = new URL(proxy);
					proxyTunnel = SharedObjectHelper.getClientBootstrap()
					        .connect(
					                new InetSocketAddress(proxyUrl.getHost(),
					                        proxyUrl.getPort() > 0 ? proxyUrl
					                                .getPort() : 80));
				}
				catch (MalformedURLException e1)
				{
					e1.printStackTrace();
				}
				proxyTunnel.addListener(new ChannelFutureListener()
				{
					public void operationComplete(ChannelFuture future)
					        throws Exception
					{
						if (!future.isSuccess())
						{
							byte[] failed = "503 Service Unavailable HTTP/1.1\r\n\r\n"
							        .getBytes();
							local.handleRawData(GoogleRemoteHandler.this,
							        ChannelBuffers.wrappedBuffer(failed));
						}
						else
						{
							ChannelPipeline pipeline = future.getChannel()
							        .getPipeline();
							pipeline.addLast("encoder",
							        new HttpRequestEncoder());
							future.getChannel().write(req);

						}
					}
				});
			}
			else
			{
				String remoteHost = HostsService
				        .getMappingHost(GoogleConfig.googleHttpsHostAlias);
				proxyTunnel = SharedObjectHelper.getClientBootstrap().connect(
				        new InetSocketAddress(remoteHost, 443));
				proxyTunnel.addListener(new ChannelFutureListener()
				{
					public void operationComplete(ChannelFuture future)
					        throws Exception
					{
						if (future.isSuccess())
						{
							byte[] established = "200 OK HTTP/1.1\r\n\r\n"
							        .getBytes();
							local.handleRawData(GoogleRemoteHandler.this,
							        ChannelBuffers.wrappedBuffer(established));
						}
						else
						{
							failedCount++;
							if (failedCount > 2)
							{
								doClose();
							}
							else
							{
								handleRequest(local, req);
							}
						}
					}
				});
			}
			proxyTunnel.getChannel().getPipeline()
			        .addLast("Forward", new SimpleChannelUpstreamHandler()
			        {
				        public void channelClosed(ChannelHandlerContext ctx,
				                ChannelStateEvent e) throws Exception
				        {
					        doClose();
				        }

				        public void messageReceived(ChannelHandlerContext ctx,
				                MessageEvent e) throws Exception
				        {
					        localHandler.handleRawData(
					                GoogleRemoteHandler.this,
					                (ChannelBuffer) e.getMessage());
				        }
			        });
		}
		else
		{
			if (!req.getUri().startsWith("http://"))
			{
				req.setUri("http://" + HttpHeaders.getHost(req) + req.getUri());
			}
			try
			{
				HttpClient c = httpsClient;
				if (!useHttps)
				{
					c = httpClient;
				}
				
				proxyClientHandler = c.execute(req,
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
				doClose();
			}

		}
	}

	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
		if (null != proxyClientHandler)
		{
			proxyClientHandler.writeBody(chunk);
		}
		else
		{
			doClose();
		}
	}

	@Override
	public void handleRawData(LocalProxyHandler local, ChannelBuffer raw)
	{
		if (null != proxyTunnel)
		{
			proxyTunnel.getChannel().write(raw);
		}
		else
		{
			doClose();
		}
	}

	private void doClose()
	{
		if (null != localHandler)
		{
			localHandler.close();
			localHandler = null;
		}
		close();
	}

	@Override
	public void close()
	{
		if (null != proxyClientHandler)
		{
			proxyClientHandler.closeChannel();
			proxyClientHandler = null;
		}
		if (null != proxyTunnel && proxyTunnel.getChannel().isOpen())
		{
			proxyTunnel.getChannel().close();
		}
		proxyTunnel = null;
	}

	@Override
	public String getName()
	{
		return "Google";
	}
}
