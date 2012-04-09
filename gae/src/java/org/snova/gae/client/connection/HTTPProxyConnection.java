/**
 * 
 */
package org.snova.gae.client.connection;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.arch.buffer.Buffer;
import org.arch.common.Pair;
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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.handler.codec.http.HttpMessageDecoder;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.framework.util.HostsHelper;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.framework.util.proxy.ProxyInfo;
import org.snova.framework.util.proxy.ProxyType;
import org.snova.gae.client.config.GAEClientConfiguration;
import org.snova.gae.client.config.GAEClientConfiguration.ConnectionMode;
import org.snova.gae.client.config.GAEClientConfiguration.GAEServerAuth;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.http.HttpServerAddress;

import com.sun.xml.internal.ws.resources.HttpserverMessages;

/**
 * @author qiyingwang
 * 
 */
public class HTTPProxyConnection extends ProxyConnection
{
	private static final int INITED = 0;
	private static final int WAITING_CONNECT_RESPONSE = 1;
	private static final int CONNECT_RESPONSED = 2;
	private static final int DISCONNECTED = 3;

	protected Logger logger = LoggerFactory.getLogger(getClass());
	private static ClientSocketChannelFactory factory;

	private AtomicBoolean waitingResponse = new AtomicBoolean(false);
	private SocketChannel clientChannel = null;

	private HttpServerAddress remoteAddress = null;
	private AtomicInteger sslProxyConnectionStatus = new AtomicInteger(0);
	private ChannelInitTask sslChannelInitTask = null;
	class ChannelInitTask
	{
		private boolean sslConnectionEnable;
		private HttpRequest request;

		ChannelInitTask(HttpRequest req, boolean isSSL)
		{
			this.request = req;
			this.sslConnectionEnable = isSSL;
		}

		public void onInitedSucceed()
		{
			clientChannel.write(request);
		}

		public void onInitFailed()
		{
			close();
		}

		public boolean isReady()
		{
			if (sslConnectionEnable)
			{
				return sslProxyConnectionStatus.get() == CONNECT_RESPONSED;
			}
			return true;
		}

		public void onVerify()
		{
			if (sslConnectionEnable)
			{
				if(!isReady())
				{
					sslChannelInitTask = this;
					return;
				}
				try
				{
					SSLContext sslContext = SSLContext.getDefault();
					SSLEngine sslEngine = sslContext.createSSLEngine();
					sslEngine.setUseClientMode(true);
					clientChannel.getPipeline().addFirst("sslHandler",
					        new SslHandler(sslEngine));
					ChannelFuture hf = clientChannel.getPipeline()
					        .get(SslHandler.class).handshake();
					hf.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							if (future.isSuccess())
							{
								onInitedSucceed();
							}
							else
							{
								onInitFailed();
							}
						}
					});
				}
				catch (Exception ex)
				{
					logger.error(null, ex);
					close();
				}
			}
			else
			{
				onInitedSucceed();
			}
		}
	}

	public HTTPProxyConnection(GAEServerAuth auth)
	{
		super(auth);
		String appid = auth.backendEnable ? (GAEConstants.BACKEND_INSTANCE_NAME
		        + "." + auth.appid) : auth.appid;
		remoteAddress = new HttpServerAddress(appid + ".appspot.com",
		        GAEConstants.HTTP_INVOKE_PATH, GAEClientConfiguration
		                .getInstance().getConnectionMode()
		                .equals(ConnectionMode.HTTPS));

	}

	public boolean isReady()
	{
		return !waitingResponse.get();
	}

	@Override
	protected void setAvailable(boolean flag)
	{
		waitingResponse.set(flag);
	}

	@Override
	protected void doClose()
	{
		waitingResponse.set(false);
		if (clientChannel != null && clientChannel.isOpen())
		{
			clientChannel.close();
		}
	}

	@Override
	protected int getMaxDataPackageSize()
	{
		return GAEConstants.APPENGINE_HTTP_BODY_LIMIT;
	}

	private ChannelInitTask initConnectedChannel(Channel connectedChannel,
	        HttpRequest req)
	{
		boolean sslConnectionEnable = false;
		ProxyInfo info = cfg.getLocalProxy();
		if (null != info)
		{
			if (info.type.equals(ProxyType.HTTPS))
			{
				sslConnectionEnable = true;
			}
			if (null != cfg.getGoogleProxyChain())
			{
				sslConnectionEnable = true;
				String httpsHost = cfg.getGoogleProxyChain().host;
				httpsHost = HostsHelper.getMappingHost(httpsHost);
				int httpsport = cfg.getGoogleProxyChain().port;

				HttpRequest request = new DefaultHttpRequest(
				        HttpVersion.HTTP_1_1, HttpMethod.CONNECT, httpsHost
				                + ":" + httpsport);
				request.setHeader(HttpHeaders.Names.HOST, httpsHost + ":"
				        + httpsport);
				if (null != info.user)
				{
					String userpass = info.user + ":" + info.passwd;
					String encode = Base64.encodeToString(userpass.getBytes(),
					        false);
					request.setHeader(HttpHeaders.Names.PROXY_AUTHORIZATION,
					        "Basic " + encode);
				}
				sslProxyConnectionStatus.set(WAITING_CONNECT_RESPONSE);
				connectedChannel.write(request);
				// String req = "CONNECT docs.google.com:443 HTTP/1.1\r\n"
				// + "Host: docs.google.com:443\r\n\r\n";
				// ChannelBuffer tmp =
				// ChannelBuffers.wrappedBuffer(req.getBytes());
				// clientChannel.write(tmp);
				if (logger.isDebugEnabled())
				{
					logger.debug("Send google chain connect resuest:" + request);
				}
			}
		}
		ChannelInitTask task = new ChannelInitTask(req, sslConnectionEnable);

		return task;
	}

	private ChannelFuture connectRemoteProxyServer(HttpServerAddress address)
	{
		ChannelPipeline pipeline = Channels.pipeline();
		// pipeline.addLast("executor",
		// new ExecutionHandler(SharedObjectHelper.getGlobalThreadPool()));
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("encoder", new HttpRequestEncoder());
		pipeline.addLast("handler", new HttpResponseHandler());
		if (null == factory)
		{
			if (null == SharedObjectHelper.getGlobalThreadPool())
			{
				ThreadPoolExecutor workerExecutor = new OrderedMemoryAwareThreadPoolExecutor(
				        20, 0, 0);
				SharedObjectHelper.setGlobalThreadPool(workerExecutor);

			}
			if (NetworkHelper.isIPV6Address(HostsHelper.getMappingHost(address.getHost())))
			{
				factory = new OioClientSocketChannelFactory(
				        SharedObjectHelper.getGlobalThreadPool());
			}
			else
			{
				factory = new NioClientSocketChannelFactory(
				        SharedObjectHelper.getGlobalThreadPool(),
				        SharedObjectHelper.getGlobalThreadPool());
			}

		}
		clientChannel = factory.newChannel(pipeline);
		String connectHost;
		int connectPort;
		boolean sslConnectionEnable = false;
		if (null != cfg.getLocalProxy())
		{
			connectHost = cfg.getLocalProxy().host;
			connectPort = cfg.getLocalProxy().port;
			if (ProxyType.HTTPS.equals(cfg.getLocalProxy().type))
			{
				sslConnectionEnable = true;
			}
		}
		else
		{
			connectHost = address.getHost();
			connectPort = address.getPort();
			sslConnectionEnable = address.isSecure();
		}
		// connectHost = cfg.getMappingHost(connectHost);
		connectHost = HostsHelper.getMappingHost(connectHost);
		if (logger.isDebugEnabled())
		{
			logger.debug("Connect remote proxy server " + connectHost + ":"
			        + connectPort + " and sslEnable:" + sslConnectionEnable);
		}
		ChannelFuture future = clientChannel.connect(new InetSocketAddress(
		        connectHost, connectPort));
		return future;
	}

	private HttpRequest buildSentRequest(String url, Buffer content)
	{
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, url);
		request.setHeader("Host",
		        remoteAddress.getHost() + ":" + remoteAddress.getPort());
		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		if (null != cfg.getLocalProxy())
		{
			ProxyInfo info = cfg.getLocalProxy();
			if (null != info.user)
			{
				String userpass = info.user + ":" + info.passwd;
				String encode = Base64.encodeToString(userpass.getBytes(),
				        false);
				request.setHeader(HttpHeaders.Names.PROXY_AUTHORIZATION,
				        "Basic " + encode);
			}
		}
		request.setHeader(HttpHeaders.Names.CONTENT_TRANSFER_ENCODING,
		        HttpHeaders.Values.BINARY);
		request.setHeader(HttpHeaders.Names.USER_AGENT, cfg.getUserAgent()
		        .trim());
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");

		ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(
		        content.getRawBuffer(), content.getReadIndex(),
		        content.readableBytes());
		request.setHeader("Content-Length",
		        String.valueOf(buffer.readableBytes()));
		request.setContent(buffer);
		return request;
	}

	protected boolean doSend(Buffer content)
	{
		waitingResponse.set(true);
		if (cfg.getConnectionMode().equals(ConnectionMode.HTTPS))
		{
			remoteAddress.trnasform2Https();
		}
		String url = remoteAddress.toPrintableString();
		if (cfg.isSimpleURLEnable())
		{
			if (null == cfg.getLocalProxy() || null == cfg.getLocalProxy().user)
			{
				url = remoteAddress.getPath();
			}
		}
		final HttpRequest request = buildSentRequest(url, content);

		if (null == clientChannel || !clientChannel.isConnected())
		{
			ChannelFuture connFuture = connectRemoteProxyServer(remoteAddress);
			connFuture.addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture future)
				        throws Exception
				{
					if (future.isSuccess())
					{
						ChannelInitTask result = initConnectedChannel(
						        future.getChannel(), request);
						result.onVerify();
					}
					else
					{
						close();
					}
				}
			});
		}
		else
		{
			clientChannel.write(request);
			if (logger.isDebugEnabled())
			{
				logger.debug("Send event via connected HTTP connection.");
			}
		}
		return true;
	}

	private void updateSSLProxyConnectionStatus(int status)
	{
		synchronized (sslProxyConnectionStatus)
		{
			sslProxyConnectionStatus.set(status);
			sslProxyConnectionStatus.notify();
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
	class HttpResponseHandler extends SimpleChannelUpstreamHandler
	{
		private boolean readingChunks = false;
		private int responseContentLength = 0;
		private Buffer resBuffer = new Buffer(0);

		private void clearBuffer()
		{
			resBuffer = new Buffer(0);
		}

		private void fillResponseBuffer(ChannelBuffer buffer)
		{
			int contentlen = buffer.readableBytes();
			resBuffer.ensureWritableBytes(contentlen);
			buffer.readBytes(resBuffer.getRawBuffer(),
			        resBuffer.getWriteIndex(), contentlen);
			resBuffer.advanceWriteIndex(contentlen);
			if (responseContentLength <= resBuffer.readableBytes())
			{
				waitingResponse.set(false);
				doRecv(resBuffer);
				clearBuffer();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			e.getChannel().close();
			// e.getCause().printStackTrace();
			logger.error("exceptionCaught in HttpResponseHandler", e.getCause());
			updateSSLProxyConnectionStatus(DISCONNECTED);
			waitingResponse.set(false);
			close();
			clearBuffer();
		}

		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Connection closed.");
			}
			updateSSLProxyConnectionStatus(DISCONNECTED);
			waitingResponse.set(false);
			close();
			clearBuffer();
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			
			if (!readingChunks)
			{
				HttpResponse response = (HttpResponse) e.getMessage();
				if (logger.isDebugEnabled())
				{
					logger.debug("Recv response:" + e.getMessage());
				}
				// workaround solution for netty
				if (casSSLProxyConnectionStatus(WAITING_CONNECT_RESPONSE,
				        CONNECT_RESPONSED))
				{
					HttpMessageDecoder decoder = e.getChannel().getPipeline()
					        .get(HttpResponseDecoder.class);
					Method m = HttpMessageDecoder.class.getDeclaredMethod(
					        "reset", null);
					m.setAccessible(true);
					m.invoke(decoder, null);
					waitingResponse.set(false);
					if(null != sslChannelInitTask)
					{
						sslChannelInitTask.onVerify();
						sslChannelInitTask = null;
					}
					return;
				}

				// responseContentLength = (int) HttpHeaders
				// .getContentLength(response);
				responseContentLength = (int) HttpHeaders
				        .getContentLength(response);
				if (response.getStatus().getCode() == 200)
				{
					if (response.isChunked())
					{
						readingChunks = true;
						waitingResponse.set(true);
					}
					else
					{
						readingChunks = false;
						waitingResponse.set(false);
					}
					ChannelBuffer content = response.getContent();
					fillResponseBuffer(content);
				}
				else
				{
					waitingResponse.set(false);
					logger.error("Received error response:" + response);
					closeRelevantSessions(response);
				}
			}
			else
			{
				HttpChunk chunk = (HttpChunk) e.getMessage();
				if (chunk.isLast())
				{
					readingChunks = false;
					waitingResponse.set(false);
				}
				fillResponseBuffer(chunk.getContent());
			}
		}
	}
}
