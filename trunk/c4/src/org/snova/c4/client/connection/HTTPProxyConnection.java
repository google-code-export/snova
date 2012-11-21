/**
 * 
 */
package org.snova.c4.client.connection;

import java.net.InetSocketAddress;

import org.arch.buffer.Buffer;
import org.arch.misc.crypto.base64.Base64;
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
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.connection.util.ConnectionHelper;
import org.snova.c4.common.C4Constants;
import org.snova.framework.util.HostsHelper;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.framework.util.proxy.ProxyInfo;

/**
 * @author qiyingwang
 * 
 */
public class HTTPProxyConnection extends ProxyConnection
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private ChannelFuture clientChannelFuture = null;

	private HttpResponseHandler responseHandler = null;
	private int connectFailedCount = 0;

	public HTTPProxyConnection(C4ServerAuth auth)
	{
		super(auth);
	}

	protected void doClose()
	{
		if (clientChannelFuture != null
		        && clientChannelFuture.getChannel().isConnected())
		{
			clientChannelFuture.getChannel().close();
		}
		clientChannelFuture = null;
	}

	private ChannelFuture connectProxyServer()
	{
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("executor",
		        new ExecutionHandler(SharedObjectHelper.getGlobalThreadPool()));
		pipeline.addLast("decoder", new HttpResponseDecoder());
		pipeline.addLast("encoder", new HttpRequestEncoder());
		responseHandler = new HttpResponseHandler();
		pipeline.addLast("handler", responseHandler);
		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		String connectHost = auth.domain;
		int connectPort = auth.port;
		C4ClientConfiguration cfg = C4ClientConfiguration.getInstance();
		if (null != cfg.getLocalProxy() && cfg.isUseGlobalProxy())
		{
			ProxyInfo info = cfg.getLocalProxy();
			connectHost = info.host;
			connectPort = info.port;
		}

		connectHost = HostsHelper.getMappingHost(connectHost);
		if (logger.isDebugEnabled())
		{
			logger.debug("#Connect remote proxy server " + connectHost + ":"
			        + connectPort);
		}
		ChannelFuture future = channel.connect(new InetSocketAddress(
		        connectHost, connectPort));

		return future;
	}

	private synchronized ChannelFuture getRemoteChannelFuture()
	{
		if (null == clientChannelFuture
		        || (clientChannelFuture.isSuccess() && !clientChannelFuture
		                .getChannel().isConnected()))
		{
			clientChannelFuture = connectProxyServer();
		}
		return clientChannelFuture;
	}

	protected void sendContent(Channel ch, final Buffer content)
	{
		String url = "http://" + auth.domain + "/invoke2";
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, url);
		request.setHeader("Host", auth.domain);
		request.setHeader(C4Constants.USER_TOKEN_HEADER,
		        ConnectionHelper.getUserToken());
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
		request.setHeader(HttpHeaders.Names.USER_AGENT, C4ClientConfiguration
		        .getInstance().getUserAgent());
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");
		String role = isPullConnection ? "pull" : "push";
		request.setHeader(
		        "C4MiscInfo",
		        role + "_" + cfg.getHTTPRequestTimeout() + "_"
		                + cfg.getMaxReadBytes());
		request.setHeader("Content-Length",
		        String.valueOf(content.readableBytes()));
		if (content.readableBytes() > 0)
		{
			ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(
			        content.getRawBuffer(), content.getReadIndex(),
			        content.readableBytes());
			request.setContent(buffer);
		}

		ch.write(request).addListener(new ChannelFutureListener()
		{
			@Override
			public void operationComplete(ChannelFuture future)
			        throws Exception
			{
				if (!future.isSuccess())
				{
					// retry
					doSend(content);
				}
			}
		});
	}

	protected boolean doSend(final Buffer content)
	{
		ChannelFuture remote = getRemoteChannelFuture();
		if (remote.getChannel().isConnected())
		{
			if (logger.isTraceEnabled())
			{
				logger.trace("Reuse connected HTTP client channel.");
			}
			sendContent(remote.getChannel(), content);
		}
		else
		{
			remote.addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture future)
				        throws Exception
				{
					if (future.isSuccess())
					{
						sendContent(future.getChannel(), content);
					}
					else
					{
						if (connectFailedCount < 3)
						{
							logger.error("Failed to connect remote c4 server, try again");
							connectFailedCount++;
							doSend(content);
						}
						else
						{
							connectFailedCount = 0;
						}
					}
				}
			});
		}
		return true;
	}

	class HttpResponseHandler extends SimpleChannelUpstreamHandler
	{
		private int responseContentLength = 0;
		private Buffer resBuffer = new Buffer(0);

		private void clearBuffer()
		{
			resBuffer = new Buffer(0);
		}

		private void transactionCompeleted()
		{
			clearBuffer();
			if (isPullConnection && isRunning)
			{
				pullData();
			}
		}

		private void fillResponseBuffer(ChannelBuffer buffer)
		{
			int contentlen = buffer.readableBytes();
			if (contentlen > 0 && isRunning)
			{
				resBuffer.ensureWritableBytes(contentlen);
				buffer.readBytes(resBuffer.getRawBuffer(),
				        resBuffer.getWriteIndex(), contentlen);
				resBuffer.advanceWriteIndex(contentlen);
				if (responseContentLength <= resBuffer.readableBytes())
				{
					doRecv(resBuffer);
					clearBuffer();
					transactionCompeleted();
				}
			}
			else
			{
				transactionCompeleted();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			logger.error("exceptionCaught in HttpResponseHandler", e.getCause());
		}

		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("C4 HTTP connection closed.");
			}
			if (responseContentLength > 0)
			{
				if (logger.isDebugEnabled())
				{
					logger.error("Not finished since no enought data read.");
				}
			}
			transactionCompeleted();
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object msg = e.getMessage();
			if (msg instanceof HttpResponse)
			{
				HttpResponse response = (HttpResponse) e.getMessage();
				responseContentLength = (int) HttpHeaders
				        .getContentLength(response);
				if (logger.isTraceEnabled())
				{
					logger.trace("Response received:" + response
					        + " with content-length:" + responseContentLength
					        + " with content-type:"
					        + response.getHeader("Content-Type"));
				}
				if (response.getStatus().getCode() == 200)
				{
					ChannelBuffer content = response.getContent();
					fillResponseBuffer(content);
				}
				else
				{
					logger.error("Received error response:" + response
					        + " for isPull:" + isPullConnection);
					byte[] buf = new byte[response.getContent().readableBytes()];
					response.getContent().readBytes(buf);
					logger.error("Server error:" + new String(buf));
					transactionCompeleted();
				}
			}
			else if (msg instanceof HttpChunk)
			{
				HttpChunk chunk = (HttpChunk) e.getMessage();
				fillResponseBuffer(chunk.getContent());
			}
			else
			{
				logger.error("Unsupported message type:"
				        + msg.getClass().getCanonicalName());
			}
		}
	}
}
