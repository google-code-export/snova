/**
 * 
 */
package org.snova.spac.session;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.misc.crypto.base64.Base64;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.snova.framework.util.HostsHelper;

/**
 * @author qiyingwang
 * 
 */
public class GoogleForwardSession extends Session
{
	private static final int INITED = 0;
	private static final int WAITING_CONNECT_RESPONSE = 1;
	private static final int CONNECT_RESPONSED = 2;
	private static final int CONNECT_FAIELD = 2;
	private static final int DISCONNECTED = 3;
	private AtomicInteger sslProxyConnectionStatus = new AtomicInteger(0);
	protected Channel remoteChannel;
	private String proxyHost;
	private int proxyPort;
	private String proxyUser;
	private String proxyPass;
	private boolean isHttps;

	public GoogleForwardSession(String sessionname)
	{
		if (sessionname.indexOf("[") != -1 && sessionname.indexOf("]") != -1)
		{
			int start = sessionname.indexOf("[");
			int end = sessionname.indexOf("]");
			String str = sessionname.substring(start + 1, end);
			String[] ss = str.split(":");
			if (ss.length == 2)
			{
				proxyHost = ss[0].trim();
				proxyPort = Integer.parseInt(ss[1].trim());
			}
			else if (ss.length == 1)
			{
				proxyHost = ss[0].trim();
				proxyPort = 8080;
			}
		}
	}

	@Override
	public SessionType getType()
	{
		return SessionType.GOOGLE_FORWARD;
	}

	private String getGoogleHttpsHost()
	{
		String s = HostsHelper.getMappingHost("GoogleHttpsIP");
		if (s == "GoogleHttpsIP")
		{
			s = HostsHelper.getMappingHost("GoogleHttps");
		}
		return s;
	}

	protected Channel getRemoteGoogleChannel(boolean handshake)
	{
		if (null != remoteChannel && remoteChannel.isConnected())
		{
			return remoteChannel;
		}
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("encoder", new HttpRequestEncoder());
		pipeline.addLast("handler", new GoogleRemoteChannelResponseHandler());
		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		String connectHost = proxyHost;
		int connectPort = proxyPort;
		if (null == proxyHost)
		{
			connectHost = getGoogleHttpsHost();
			connectPort = 443;
		}
		connectHost = HostsHelper.getMappingHost(connectHost);
		if (logger.isDebugEnabled())
		{
			logger.debug("Connect remote address " + connectHost + ":"
			        + connectPort);
		}
		ChannelFuture future = channel.connect(
		        new InetSocketAddress(connectHost, connectPort))
		        .awaitUninterruptibly();
		int retry = 3;
		while (!future.isSuccess() && null == proxyHost && retry > 0)
		{
			logger.error("Failed to connect forward address.",
			        future.getCause());
			connectHost = getGoogleHttpsHost();
			future = channel.connect(
			        new InetSocketAddress(connectHost, connectPort))
			        .awaitUninterruptibly();
			retry--;
		}
		if(!future.isSuccess())
		{
			return null;
		}
		if (null != proxyHost)
		{
			if(logger.isDebugEnabled())
			{
				logger.debug("Start Send Connect Request!");
			}
			HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
			        HttpMethod.CONNECT, getGoogleHttpsHost() + ":" + 443);
			request.setHeader(HttpHeaders.Names.HOST, getGoogleHttpsHost()
			        + ":443");
			if (null != proxyUser)
			{
				String userpass = proxyUser + ":" + proxyPass;
				String encode = Base64.encodeToString(userpass.getBytes(),
				        false);
				request.setHeader(HttpHeaders.Names.PROXY_AUTHORIZATION,
				        "Basic " + encode);
			}
			sslProxyConnectionStatus.set(WAITING_CONNECT_RESPONSE);
			channel.write(request);
			synchronized (sslProxyConnectionStatus)
			{
				try
				{
					sslProxyConnectionStatus.wait(60000);
					if (sslProxyConnectionStatus.get() != CONNECT_RESPONSED)
					{
						return null;
					}
				}
				catch (InterruptedException e)
				{
					//
				}
				finally
				{
					sslProxyConnectionStatus.set(INITED);
				}
			}
		}
		remoteChannel = channel;
		if(!handshake)
		{
			removeCodecHandler(remoteChannel, null);
			return remoteChannel;
		}
		try
		{
			
			SSLContext sslContext = SSLContext.getDefault();
			SSLEngine sslEngine = sslContext.createSSLEngine();
			sslEngine.setUseClientMode(true);
			pipeline.addFirst("sslHandler", new SslHandler(sslEngine));
			ChannelFuture hf = channel.getPipeline().get(SslHandler.class)
			        .handshake(channel);
			hf.awaitUninterruptibly();
			if (!hf.isSuccess())
			{
				logger.error("Handshake failed", hf.getCause());
				channel.close();
				return null;
			}
			//channel.getPipeline().remove("sslHandler");
			removeCodecHandler(remoteChannel, null);
			if (logger.isDebugEnabled())
			{
				logger.debug("SSL handshake success!");
			}
		}
		catch (Exception ex)
		{
			logger.error(null, ex);
			channel.close();
			return null;
		}
		return channel;
	}

	@Override
	protected void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) event;
				
				if (req.method.equalsIgnoreCase("Connect"))
				{
					Channel ch = getRemoteGoogleChannel(false);
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1,
					        ch != null ? HttpResponseStatus.OK
					                : HttpResponseStatus.SERVICE_UNAVAILABLE);
					ChannelFuture future = localChannel.write(res);
					removeCodecHandler(localChannel, future);
					//remoteChannel.getPipeline().remove("sslHandler");
					return;
				}
				removeCodecHandler(localChannel, null);
				Channel ch = getRemoteGoogleChannel(true);
				ChannelBuffer msg = buildRequestChannelBuffer((HTTPRequestEvent) event);
				ch.write(msg);
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				ChannelBuffer buf = ChannelBuffers.wrappedBuffer(chunk.content);
				if (null != remoteChannel)
				{
					remoteChannel.write(buf);
				}
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				HTTPConnectionEvent ev = (HTTPConnectionEvent) event;
				if (ev.status == HTTPConnectionEvent.CLOSED)
				{
					closeRemote();
				}
				break;
			}
			default:
			{
				logger.error("Unexpected event type:" + header.type);
				break;
			}
		}
	}

	protected void closeRemote()
	{
		if (null != remoteChannel)
		{
			remoteChannel.close();
			remoteChannel = null;
		}
	}

	private boolean casSSLProxyConnectionStatus(int current, int status)
	{
		synchronized (sslProxyConnectionStatus)
		{
			int cur = sslProxyConnectionStatus.get();
			if (cur != current)
			{
				return false;
			}
			sslProxyConnectionStatus.set(status);
			sslProxyConnectionStatus.notify();
			return true;
		}
	}

	@ChannelPipelineCoverage("one")
	class GoogleRemoteChannelResponseHandler extends
	        SimpleChannelUpstreamHandler
	{
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Connection closed.");
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			logger.error("exceptionCaught in RemoteChannelResponseHandler",
			        e.getCause());
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object obj = e.getMessage();
			if (obj instanceof ChannelBuffer)
			{
				if (null != localChannel && localChannel.isConnected())
				{
					localChannel.write(obj);
				}
				else
				{
					logger.error("Local browser channel is not connected.");
					closeRemote();
				}
			}
			else if (obj instanceof HttpResponse)
			{
				if(logger.isDebugEnabled())
				{
					logger.debug("Recv Connect Response!");
				}
				HttpResponse response = (HttpResponse) obj;
				if (response.getStatus().getCode() >= 400)
				{
					casSSLProxyConnectionStatus(WAITING_CONNECT_RESPONSE,
					        CONNECT_FAIELD);
					return;
				}
				if (casSSLProxyConnectionStatus(WAITING_CONNECT_RESPONSE,
				        CONNECT_RESPONSED))
				{
//					 HttpMessageDecoder decoder = e.getChannel().getPipeline()
//					 .get(HttpResponseDecoder.class);
//					 Method m = HttpMessageDecoder.class.getDeclaredMethod(
//					 "reset", null);
//					 m.setAccessible(true);
//					 m.invoke(decoder, null);
//					
//					return;
				}
			}
			else
			{
				logger.error("Unexpected message type:"
				        + obj.getClass().getName());
			}
		}
	}

}
