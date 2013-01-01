/**
 * 
 */
package org.snova.framework.proxy.forward;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.server.ProxyHandler;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientHandler;

/**
 * @author qiyingwang
 * 
 */
public class ForwardRemoteHandler implements RemoteProxyHandler
{
	private static HttpClient directHttpClient;

	private LocalProxyHandler localHandler;
	private HttpClientHandler proxyClientHandler;
	private ChannelFuture proxyTunnel;

	private static void initHttpClient() throws Exception
	{
		if (null != directHttpClient)
		{
			return;
		}
		directHttpClient = new HttpClient(null,
		        SharedObjectHelper.getClientBootstrap());
	}

	public ForwardRemoteHandler()
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
			int port = 443;
			if (address.indexOf(":") != -1)
			{
				host = address.split(":")[0];
				port = Integer.parseInt(address.split(":")[1]);
			}
			proxyTunnel = SharedObjectHelper.getClientBootstrap().connect(
			        new InetSocketAddress(host, port));
			proxyTunnel.addListener(new ChannelFutureListener()
			{
				public void operationComplete(ChannelFuture future)
				        throws Exception
				{
					if (future.isSuccess())
					{
						byte[] established = "200 OK HTTP/1.1\r\n\r\n"
						        .getBytes();
						local.handleRawData(ForwardRemoteHandler.this,
						        ChannelBuffers.wrappedBuffer(established));
					}
					else
					{
						close();
						local.onProxyFailed(ForwardRemoteHandler.this, req);
					}
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
	    return "";
    }

}
