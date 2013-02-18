/**
 * 
 */
package org.snova.framework.proxy.forward;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.google.GoogleRemoteHandler;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.proxy.spac.filter.GFWList;
import org.snova.framework.server.ProxyHandler;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientHandler;

/**
 * @author yinqiwen
 * 
 */
public class ForwardRemoteHandler implements RemoteProxyHandler
{
	protected static Logger	  logger	= LoggerFactory
	                                         .getLogger(ForwardRemoteHandler.class);
	private static HttpClient	directHttpClient;
	
	private LocalProxyHandler	localHandler;
	private HttpClientHandler	proxyClientHandler;
	private ChannelFuture	  proxyTunnel;
	private URL	              targetAddress;
	
	private static void initHttpClient() throws Exception
	{
		if (null != directHttpClient)
		{
			return;
		}
		directHttpClient = new HttpClient(null,
		        SharedObjectHelper.getClientBootstrap());
	}
	
	public ForwardRemoteHandler(Map<String, String> attrs)
	{
		try
		{
			initHttpClient();
			for (String attr : attrs.keySet())
			{
				if (attr.startsWith("http://") || attr.startsWith("socks://"))
				{
					targetAddress = new URL(attr);
				}
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			String address = req.getUri();
			String host = address;
			final String x = host;
			int port = 443;
			if (address.indexOf(":") != -1)
			{
				host = address.split(":")[0];
				port = Integer.parseInt(address.split(":")[1]);
			}
			host = HostsService.getRealHost(host, port);
			logger.info("Find " + host + " for " + x);
			final InetSocketAddress remote = new InetSocketAddress(host, port);
			proxyTunnel = SharedObjectHelper.getClientBootstrap().connect(
			        remote);
			proxyTunnel.getChannel().getConfig().setConnectTimeoutMillis(5000);
			proxyTunnel.addListener(new ChannelFutureListener()
			{
				public void operationComplete(ChannelFuture future)
				        throws Exception
				{
					if (future.isSuccess())
					{
						byte[] established = "HTTP/1.1 200 Connection established\r\n\r\n"
						        .getBytes();
						local.handleRawData(ForwardRemoteHandler.this,
						        ChannelBuffers.wrappedBuffer(established));
					}
					else
					{
						close();
						local.onProxyFailed(ForwardRemoteHandler.this, req);
						logger.warn("Failed to connect " + remote);
					}
				}
			});
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
					                ForwardRemoteHandler.this,
					                (ChannelBuffer) e.getMessage());
				        }
			        });
		}
		else
		{
			try
			{
				proxyClientHandler = directHttpClient.execute(req,
				        new FutureCallback.FutureCallbackAdapter()
				        {
					        public void onResponse(HttpResponse res)
					        {
						        local.handleResponse(ForwardRemoteHandler.this,
						                res);
					        }
					        
					        public void onBody(HttpChunk chunk)
					        {
						        local.handleChunk(ForwardRemoteHandler.this,
						                chunk);
					        }
					        
					        public void onError(String error)
					        {
						        close();
						        local.onProxyFailed(ForwardRemoteHandler.this,
						                req);
					        }
				        });
			}
			catch (HttpClientException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	
	private void doClose()
	{
		close();
		if (null != localHandler)
		{
			localHandler.close();
			localHandler = null;
		}
	}
	
	@Override
	public String getName()
	{
		return "Forward";
	}
	
}
