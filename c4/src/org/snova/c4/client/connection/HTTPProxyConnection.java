/**
 * 
 */
package org.snova.c4.client.connection;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

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
	// private AtomicBoolean waitingResponse = new AtomicBoolean(
	// false);
	private ChannelFuture clientChannelFuture = null;
	private int connectFailedCount;
	private long lastWriteTime;
	private int index;

	private HttpResponseHandler responseHandler = null;

	public HTTPProxyConnection(C4ServerAuth auth, int index)
	{
		super(auth);
		this.index = index;
	}

	// public HTTPProxyConnection(C4ServerAuth auth, boolean pri)
	// {
	//
	// isPrimary = pri;
	// }

	public boolean isReady()
	{
		long now = System.currentTimeMillis();
		if (now - lastWriteTime >= C4ClientConfiguration.getInstance()
		        .getHTTPRequestTimeout())
		{
			if (null != clientChannelFuture
			        && clientChannelFuture.getChannel().isConnected())
			{
				clientChannelFuture.getChannel().close();
			}
			return true;
		}
		if (now - lastWriteTime < C4ClientConfiguration.getInstance()
		        .getMinWritePeriod())
		{
			return false;
		}
		if (null == clientChannelFuture
		        || !clientChannelFuture.getChannel().isConnected())
		{
			return true;
		}
		return responseHandler.isTransactionCompeleted();
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
		// pipeline.addLast("aggregator", new
		// HttpChunkAggregator(maxMessageSize));
		pipeline.addLast("encoder", new HttpRequestEncoder());
		responseHandler = new HttpResponseHandler();
		pipeline.addLast("handler", responseHandler);
		SocketChannel channel = getClientSocketChannelFactory().newChannel(
		        pipeline);
		String connectHost = auth.domain;
		int connectPort = auth.port;
		C4ClientConfiguration cfg = C4ClientConfiguration.getInstance();
		if (null != cfg.getLocalProxy())
		{
			ProxyInfo info = cfg.getLocalProxy();
			connectHost = info.host;
			connectPort = info.port;
		}
		// boolean sslConnectionEnable = false;

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
		String url = "http://" + auth.domain + "/invoke";
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, url);
		request.setHeader("Host", auth.domain);
		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		// request.setHeader(HttpHeaders.Names.CONNECTION, "close");
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
		request.setHeader("TransactionTime", C4ClientConfiguration
		        .getInstance().getPullTransactionTime());
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");
		request.setHeader("MaxResponseSize", 512 * 1024 + "");

		ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(
		        content.getRawBuffer(), content.getReadIndex(),
		        content.readableBytes());
		request.setHeader("Content-Length",
		        String.valueOf(buffer.readableBytes()));
		request.setContent(buffer);
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
		responseHandler.startTransaction();
		if (logger.isTraceEnabled())
		{
			logger.trace("Send event via HTTP connection.");
		}
	}

	protected boolean doSend(final Buffer content)
	{
		lastWriteTime = System.currentTimeMillis();
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
						connectFailedCount = 0;
					}
					else
					{
						connectFailedCount++;
						if (connectFailedCount < 3)
						{
							logger.error("Failed to connect remote c4 server, try again");
							// waitingResponse.set(false);
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

	class HttpResponseHandler extends SimpleChannelUpstreamHandler implements
	        Runnable
	{
		private AtomicBoolean unanwsered = new AtomicBoolean(false);
		private AtomicBoolean receivingResponse = new AtomicBoolean(false);
		private long lastDataRecvTime = -1;
		private boolean finished = false;
		private int responseContentLength = 0;
		private Buffer resBuffer = new Buffer(0);

		private boolean isTransactionCompeleted()
		{
			return !unanwsered.get() && !receivingResponse.get();
		}

		private void clearBuffer()
		{
			resBuffer = new Buffer(0);
		}

		private void transactionCompeleted()
		{
			clearBuffer();
			unanwsered.set(false);
			receivingResponse.set(false);
			onAvailable();
		}

		private void transactionTimeout()
		{
			if (!unanwsered.get() && receivingResponse.get())
			{
				long now = System.currentTimeMillis();
				if (now - lastDataRecvTime < C4ClientConfiguration
				        .getInstance().getHeartBeatPeriod())
				{
					// transactionTask = SharedObjectHelper.getGlobalTimer()
					// .schedule(this, 1, TimeUnit.SECONDS);
					return;
				}
			}
			// transactionTask = null;
			logger.error("C4 HTTP " + HTTPProxyConnection.this.hashCode()
			        + " transaction timeout!");
			transactionFailed();
		}

		private void transactionFailed()
		{
			// if (null != transactionTask)
			// {
			// transactionTask.cancel(true);
			// transactionTask = null;
			// }
			clearBuffer();
			unanwsered.set(false);
			receivingResponse.set(false);
			close();
			onAvailable();

		}

		private void startTransaction()
		{
			// if (null != transactionTask)
			// {
			// transactionTask.cancel(true);
			// transactionTask = null;
			// }
			unanwsered.set(true);
			receivingResponse.set(false);
			// transactionTask = SharedObjectHelper.getGlobalTimer()
			// .schedule(
			// this,
			// C4ClientConfiguration.getInstance()
			// .getHTTPRequestTimeout(),
			// TimeUnit.MILLISECONDS);
		}

		private void fillResponseBuffer(ChannelBuffer buffer)
		{
			int contentlen = buffer.readableBytes();
			if (contentlen > 0)
			{
				resBuffer.ensureWritableBytes(contentlen);
				buffer.readBytes(resBuffer.getRawBuffer(),
				        resBuffer.getWriteIndex(), contentlen);
				resBuffer.advanceWriteIndex(contentlen);
				if (responseContentLength <= resBuffer.readableBytes())
				{
					doRecv(resBuffer);
					clearBuffer();
					finished = true;
					transactionCompeleted();
				}
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			// e.getCause().printStackTrace();
			logger.error("exceptionCaught in HttpResponseHandler", e.getCause());
			// updateSSLProxyConnectionStatus(DISCONNECTED);
			// close();
		}

		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("C4 HTTP connection closed.");
			}
			if (!finished && responseContentLength > 0)
			{
				if (logger.isDebugEnabled())
				{
					logger.error("Not finished since no enought data read.");
				}
			}
			// updateSSLProxyConnectionStatus(DISCONNECTED);
			transactionFailed();
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			lastDataRecvTime = System.currentTimeMillis();
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
					unanwsered.set(false);
					if (response.isChunked())
					{
						receivingResponse.set(true);
					}
					ChannelBuffer content = response.getContent();
					fillResponseBuffer(content);
				}
				else
				{
					transactionFailed();
					logger.error("Received error response:" + response);
					byte[] buf = new byte[response.getContent().readableBytes()];
					response.getContent().readBytes(buf);
					logger.error("Server error:" + new String(buf));
					// closeRelevantSessions(response);
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

		@Override
		public void run()
		{
			transactionTimeout();
		}
	}

	@Override
	protected int getMaxDataPackageSize()
	{
		return -1;
	}

}
