/**
 * 
 */
package org.snova.c4.client.connection;

import java.net.InetSocketAddress;
import java.util.concurrent.ThreadPoolExecutor;
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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
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
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.config.C4ClientConfiguration.ProxyInfo;
import org.snova.framework.util.HostsHelper;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public class HTTPProxyConnection extends ProxyConnection
{	
	protected Logger	                      logger	      = LoggerFactory
	                                                                  .getLogger(getClass());
	private static ClientSocketChannelFactory	factory;
	
	private AtomicBoolean	                  waitingResponse	= new AtomicBoolean(
	                                                                  false);
	private SocketChannel	                  clientChannel	  = null;

	private int connectFailedCount;
	private long lastWriteTime;
	
	public HTTPProxyConnection(C4ServerAuth auth)
	{
		super(auth);
	}
	
	protected void setAvailable(boolean flag)
	{
		waitingResponse.set(flag);
	}
	
	public boolean isReady()
	{
		long now = System.currentTimeMillis();
		if(now - lastWriteTime >= C4ClientConfiguration.getInstance().getHTTPRequestTimeout())
		{
			return true;
		}
		return !waitingResponse.get();
	}
	
	protected void doClose()
	{
		if (clientChannel != null && clientChannel.isOpen())
		{
			clientChannel.close();
		}
		clientChannel = null;
		waitingResponse.set(false);
	}

	
	private ChannelFuture connectProxyServer()
	{
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("executor", new ExecutionHandler(
		        SharedObjectHelper.getGlobalThreadPool()));
		pipeline.addLast("decoder", new HttpResponseDecoder());
		// pipeline.addLast("aggregator", new
		// HttpChunkAggregator(maxMessageSize));
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
			factory = new NioClientSocketChannelFactory(
			        SharedObjectHelper.getGlobalThreadPool(),
			        SharedObjectHelper.getGlobalThreadPool());
			
		}
		SocketChannel channel = factory.newChannel(pipeline);
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
			logger.debug("Connect remote proxy server " + connectHost + ":"
			        + connectPort);
		}
		ChannelFuture future = channel.connect(
		        new InetSocketAddress(connectHost, connectPort));
		//DefaultChannelFuture tmp = (DefaultChannelFuture) future;
		//DefaultChannelFuture.setUseDeadLockChecker(false);
		//future = future.awaitUninterruptibly();
		//if (!future.isSuccess())
		//{
		//	logger.error("Failed to connect proxy server.", future.getCause());
		//	return null;
		//}
		clientChannel = channel;
		return future;
	}
	
	protected void sendContent(Channel ch, Buffer content)
	{
		String url = "http://" + auth.domain + "/invoke";
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, url);
		request.setHeader("Host", auth.domain);
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
		request.setHeader(
		        HttpHeaders.Names.USER_AGENT,
		        C4ClientConfiguration.getInstance().getUserAgent());
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");
		
		ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(
		        content.getRawBuffer(), content.getReadIndex(),
		        content.readableBytes());
		request.setHeader("Content-Length",
		        String.valueOf(buffer.readableBytes()));
		request.setContent(buffer);
		ch.write(request);
		if (logger.isTraceEnabled())
		{
			logger.trace("Send event via HTTP connection.");
		}
	}

	protected boolean doSend(final Buffer content)
	{
		waitingResponse.set(true);
		lastWriteTime = System.currentTimeMillis();
		if(null == clientChannel || !clientChannel.isConnected())
		{
			clientChannel = null;
			ChannelFuture future = connectProxyServer();
			future.addListener(new ChannelFutureListener()
			{
				@Override
				public void operationComplete(ChannelFuture future) throws Exception
				{
					future = future.awaitUninterruptibly();
					if(future.isSuccess())
					{	
						sendContent(future.getChannel(), content);
						connectFailedCount = 0;
					}
					else
					{
						connectFailedCount++;
						if(connectFailedCount < 3)
						{
							logger.error("Failed to connect remote c4 server, try again");
							//waitingResponse.set(false);
							doSend(content);
						}
						else
						{
							connectFailedCount = 0;
							waitingResponse.set(false);
						}
					}
					
				}
			});
		}
		else
		{
			if(logger.isTraceEnabled())
			{
				logger.trace("Reuse connected HTTP client channel.");
			}
			sendContent(clientChannel, content);
		}
		return true;
	}
	
	class HttpResponseHandler extends SimpleChannelUpstreamHandler
	{
		private boolean	readingChunks		  = false;
		private boolean finished = false;
		private int		responseContentLength	= 0;
		private Buffer	resBuffer		      = new Buffer(0);
		
		private void clearBuffer()
		{
			resBuffer = new Buffer(0);
		}
		
		private void fillResponseBuffer(ChannelBuffer buffer)
		{
			int contentlen = buffer.readableBytes();
			if(contentlen > 0)
			{
				resBuffer.ensureWritableBytes(contentlen);
				buffer.readBytes(resBuffer.getRawBuffer(),
				        resBuffer.getWriteIndex(), contentlen);
				resBuffer.advanceWriteIndex(contentlen);
				if (responseContentLength <= resBuffer.readableBytes())
				{
					waitingResponse.set(false);
					doRecv(resBuffer);
					clearBuffer();
					finished = true;
				}
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			e.getChannel().close();
			// e.getCause().printStackTrace();
			logger.error("exceptionCaught in HttpResponseHandler", e.getCause());
			// updateSSLProxyConnectionStatus(DISCONNECTED);
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
			if(!finished)
			{
				if(logger.isDebugEnabled())
				{
					logger.error("Not finished since no enought data read.");
				}
			}
			// updateSSLProxyConnectionStatus(DISCONNECTED);
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
				responseContentLength = (int) HttpHeaders.getContentLength(response);
				if (logger.isTraceEnabled())
				{
					logger.trace("Response received:"+response + " with content-length:"+responseContentLength + " with content-type:" + response.getHeader("Content-Type"));
				}
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
						//clientChannel.close();
					}
					ChannelBuffer content = response.getContent();
					fillResponseBuffer(content);
				}
				else
				{
					waitingResponse.set(false);
					//clientChannel.close();
					logger.error("Received error response:" + response);
					if(response.getStatus().getCode() == 400)
					{
						byte[] buf = new byte[response.getContent().readableBytes()];
						response.getContent().readBytes(buf);
						logger.error("Server error:" + new String(buf));
					}
					//closeRelevantSessions(response);
				}
			}
			else
			{
				HttpChunk chunk = (HttpChunk) e.getMessage();
				fillResponseBuffer(chunk.getContent());
				if (chunk.isLast())
				{
					readingChunks = false;
					waitingResponse.set(false);
					//clientChannel.close();
				}
			}
		}
	}
	
	@Override
	protected int getMaxDataPackageSize()
	{
		return -1;
	}
	
}
