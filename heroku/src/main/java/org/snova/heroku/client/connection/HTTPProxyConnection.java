/**
 * 
 */
package org.snova.heroku.client.connection;

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
import org.jboss.netty.channel.ChannelPipelineCoverage;
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
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.HostsHelper;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.config.HerokuClientConfiguration.HerokuServerAuth;
import org.snova.heroku.client.config.HerokuClientConfiguration.ProxyInfo;

/**
 * @author qiyingwang
 * 
 */
public class HTTPProxyConnection extends ProxyConnection
{
	// private static final int INITED = 0;
	// private static final int WAITING_CONNECT_RESPONSE = 1;
	// private static final int CONNECT_RESPONSED = 2;
	// private static final int DISCONNECTED = 3;
	
	protected Logger	                      logger	      = LoggerFactory
	                                                                  .getLogger(getClass());
	private static ClientSocketChannelFactory	factory;
	
	private AtomicBoolean	                  waitingResponse	= new AtomicBoolean(
	                                                                  false);
	private SocketChannel	                  clientChannel	  = null;
	
	private String	                          remoteAddress;
	
	public HTTPProxyConnection(HerokuServerAuth auth)
	{
		super(auth);
		remoteAddress = auth.domain;
		
	}
	
	protected void setAvailable(boolean flag)
	{
		waitingResponse.set(flag);
	}
	
	public boolean isReady()
	{
		if(null == clientChannel || !clientChannel.isConnected())
		{
			waitingResponse.set(false);
		}
		return !waitingResponse.get();
	}
	
	protected void doClose()
	{
		if (clientChannel != null && clientChannel.isOpen())
		{
			//clientChannel.close();
		}
		clientChannel = null;
		waitingResponse.set(false);
	}

	
	private ChannelFuture connectProxyServer(String address)
	{
		ChannelPipeline pipeline = Channels.pipeline();
		//pipeline.addLast("executor", new ExecutionHandler(
		//        SharedObjectHelper.getGlobalThreadPool()));
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
		String connectHost = remoteAddress;
		int connectPort = 8080;
		HerokuClientConfiguration cfg = HerokuClientConfiguration.getInstance();
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
		String url = "http://" + remoteAddress + "/invoke";
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_0,
		        HttpMethod.POST, url);
		request.setHeader("Host", remoteAddress);
		request.setHeader(HttpHeaders.Names.CONNECTION, "close");
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
		        "Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US) AppleWebKit/532.5 (KHTML, like Gecko) Chrome/4.1.249.1045 Safari/532.5");
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");
		
		ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(
		        content.getRawBuffer(), content.getReadIndex(),
		        content.readableBytes());
		request.setHeader("Content-Length",
		        String.valueOf(buffer.readableBytes()));
		request.setContent(buffer);
		ch.write(request);
		if (logger.isDebugEnabled())
		{
			logger.debug("Send event via HTTP connection.");
		}
	}
//	protected void doSend(List<Event> events)
//	{
//		waitingResponse.set(true);
//		clientChannel = null;
//		ChannelFuture future = connectProxyServer(remoteAddress);
//		future.addListener(new ChannelFutureListener()
//		{
//			@Override
//			public void operationComplete(ChannelFuture future) throws Exception
//			{
//				future = future.awaitUninterruptibly();
//				if(future.isSuccess())
//				{	
//					sendContent(future.getChannel(), content);
//				}
//				else
//				{
//					logger.error("Failed to connect remote heroku server, try again");
//					waitingResponse.set(false);
//					doSend(content);
//				}
//			}
//		});
//	}
	protected boolean doSend(final Buffer content)
	{
		waitingResponse.set(true);
		clientChannel = null;
		ChannelFuture future = connectProxyServer(remoteAddress);
		future.addListener(new ChannelFutureListener()
		{
			
			@Override
			public void operationComplete(ChannelFuture future) throws Exception
			{
				future = future.awaitUninterruptibly();
				if(future.isSuccess())
				{	
					sendContent(future.getChannel(), content);
				}
				else
				{
					logger.error("Failed to connect remote heroku server, try again");
					waitingResponse.set(false);
					doSend(content);
				}
				
			}
		});
		return true;
	}
	
	@ChannelPipelineCoverage("one")
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
				logger.error("Not finished since no enought data read.");
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
				responseContentLength = (int) response.getContentLength();
				if (logger.isDebugEnabled())
				{
					logger.debug("Response received:"+response + " with content-length:"+responseContentLength + " with content-type:" + response.getHeader("Content-Type"));
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
