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
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
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
import org.snova.framework.config.DesktopFrameworkConfiguration;
import org.snova.framework.util.HostsHelper;

/**
 * @author qiyingwang
 * 
 */
public class GoogleForwardSession extends Session
{
	private static final int	INITED	                 = 0;
	private static final int	WAITING_CONNECT_RESPONSE	= 1;
	private static final int	CONNECT_RESPONSED	     = 2;
	private static final int	CONNECT_FAIELD	         = 2;
	private AtomicInteger	 sslProxyConnectionStatus	 = new AtomicInteger(0);
	protected Channel	     remoteChannel;
	private String	         proxyHost;
	private int	             proxyPort;
	private String	         proxyUser;
	private String	         proxyPass;
	private int	             retryCount	                 = 0;
	
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
				proxyPort = 80;
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
		return  HostsHelper.getMappingHost("GoogleHttps");
	}
	
	protected SocketChannel newRemoteGoogleChannel()
	{
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("encoder", new HttpRequestEncoder());
		pipeline.addLast("handler", new GoogleRemoteChannelResponseHandler());
		return getClientSocketChannelFactory().newChannel(pipeline);
	}
	
	protected void initConnectedChannel(boolean handshake,
	        ChannelFuture future, final ChannelFutureListener listener)
	{
		if (null != proxyHost)
		{
			if (logger.isDebugEnabled())
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
			future.getChannel().write(request);
			synchronized (sslProxyConnectionStatus)
			{
				try
				{
					sslProxyConnectionStatus.wait(60000);
					if (sslProxyConnectionStatus.get() != CONNECT_RESPONSED)
					{
						closeLocalChannel();
						remoteChannel = null;
						return;
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
		remoteChannel = future.getChannel();
		if (!handshake)
		{
			removeCodecHandler(remoteChannel);
			try
			{
				listener.operationComplete(null);
			}
			catch (Exception e)
			{
				//
			}
			return;
		}
		try
		{
			SSLContext sslContext = SSLContext.getDefault();
			SSLEngine sslEngine = sslContext.createSSLEngine();
			sslEngine.setUseClientMode(true);
			SslHandler sl = null;
			
			remoteChannel.getPipeline().addFirst("sslHandler",
			        new SslHandler(sslEngine));
			ChannelFuture hf = remoteChannel.getPipeline()
			        .get(SslHandler.class).handshake();
			hf.addListener(new ChannelFutureListener()
			{
				
				@Override
				public void operationComplete(ChannelFuture fa)
				        throws Exception
				{
					if (!fa.isSuccess())
					{
						logger.error("Handshake failed", fa.getCause());
						closeRemote();
						closeLocalChannel();
						return;
					}
					removeCodecHandler(remoteChannel);
					if (logger.isDebugEnabled())
					{
						logger.debug("SSL handshake success!");
					}
					listener.operationComplete(null);
				}
			});
		}
		catch (Exception ex)
		{
			logger.error(null, ex);
			closeRemote();
		}
	}
	
	protected void getGoogleChannel(final boolean handshake,
	        final ChannelFutureListener listener)
	{
		if (null != remoteChannel && remoteChannel.isConnected())
		{
			try
			{
				listener.operationComplete(null);
			}
			catch (Exception e)
			{
				
			}
			return;
		}
		SocketChannel channel = newRemoteGoogleChannel();
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
		if (!NetworkHelper.checkIp(connectHost))
		{
			connectHost = HostsHelper.lookupIP(connectHost);
		}
		ChannelFuture future = channel.connect(new InetSocketAddress(
		        connectHost, connectPort));
		future.addListener(new ChannelFutureListener()
		{
			@Override
			public void operationComplete(ChannelFuture future)
			        throws Exception
			{
				if (!future.isSuccess())
				{
					retryCount++;
					if (retryCount < 3)
					{
						getGoogleChannel(handshake, listener);
					}
					else
					{
						retryCount = 0;
						closeLocalChannel();
					}
				}
				else
				{
					retryCount = 0;
					initConnectedChannel(handshake, future, listener);
				}
			}
		});
	}
	
	@Override
	protected void onEvent(EventHeader header, final Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) event;
				
				if (req.method.equalsIgnoreCase("Connect"))
				{
					getGoogleChannel(false, new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							String msg = "HTTP/1.1 200 Connection established\r\n"
							        + "Connection: Keep-Alive\r\n"
							        + "Proxy-Connection: Keep-Alive\r\n\r\n";
							
							// HttpResponse res = new DefaultHttpResponse(
							// HttpVersion.HTTP_1_1,
							// remoteChannel != null ? HttpResponseStatus.OK
							// : HttpResponseStatus.SERVICE_UNAVAILABLE);
							// localChannel.setReadable(false);
							removeCodecHandler(localChannel);
							localChannel.write(ChannelBuffers.wrappedBuffer(msg
							        .getBytes()));
							
						}
					});
					// remoteChannel.getPipeline().remove("sslHandler");
					return;
				}
				getGoogleChannel(true, new ChannelFutureListener()
				{
					
					@Override
					public void operationComplete(ChannelFuture arg0)
					        throws Exception
					{
						if (null == remoteChannel)
						{
							HttpResponse res = new DefaultHttpResponse(
							        HttpVersion.HTTP_1_1,
							        HttpResponseStatus.SERVICE_UNAVAILABLE);
							localChannel.write(res);
							return;
						}
						if (DesktopFrameworkConfiguration.getInstance()
						        .getProxyEventHandler()
						        .equalsIgnoreCase("Google"))
						{
							// remove codec handlers for performance
							removeCodecHandler(localChannel);
						}
						// removeCodecHandler(localChannel, null);
						ChannelBuffer msg = buildRequestChannelBuffer(
						        (HTTPRequestEvent) event, true);
						remoteChannel.write(msg);
					}
				});
				
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
			SslHandler ssl = remoteChannel.getPipeline().get(SslHandler.class);
			if (null != ssl)
			{
				ssl.close().addListener(new ChannelFutureListener()
				{
					@Override
					public void operationComplete(ChannelFuture future)
					        throws Exception
					{
						if (remoteChannel.isConnected())
						{
							remoteChannel.close();
						}
						
						remoteChannel = null;
					}
				});
			}
			else
			{
				if (remoteChannel.isConnected())
				{
					remoteChannel.close();
				}
				remoteChannel = null;
			}
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
	
	// @ChannelPipelineCoverage("one")
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
				if (logger.isDebugEnabled())
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
