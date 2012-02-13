/**
 * 
 */
package org.snova.c4.client.connection.v2;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.util.RandomHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.framework.util.HostsHelper;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.framework.util.proxy.ProxyInfo;

/**
 * @author wqy
 * 
 */
abstract class HTTPPersistentConnection implements Runnable
{
	private static ClientSocketChannelFactory	factory;
	private static String	                  userToken	      = null;
	protected static Logger	                  logger	      = LoggerFactory
	                                                                  .getLogger(HTTPPersistentConnection.class);
	protected ChannelFuture	                  remoteFuture	  = null;
	protected C4ServerAuth	                  auth;
	protected AtomicBoolean	                  waitingResponse	= new AtomicBoolean(
	                                                                  false);
	protected HTTPProxyConnectionV2	          conn;
	
	static ClientSocketChannelFactory getClientSocketChannelFactory()
	{
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
		return factory;
	}
	
	public HTTPPersistentConnection(C4ServerAuth auth,
	        HTTPProxyConnectionV2 conn)
	{
		this.auth = auth;
		this.conn = conn;
	}
	
	public boolean isWaitingResponse()
	{
		return waitingResponse.get();
	}
	
	protected static String getUserToken()
	{
		if (null != userToken)
		{
			return userToken;
		}
		try
		{
			byte[] mac = NetworkInterface.getByInetAddress(
			        InetAddress.getLocalHost()).getHardwareAddress();
			StringBuffer sb = new StringBuffer();
			
			for (int i = 0; i < mac.length; i++)
			{
				if (i != 0)
				{
					sb.append("-");
				}
				String s = Integer.toHexString(mac[i] & 0xFF);
				sb.append(s.length() == 1 ? 0 + s : s);
			}
			userToken = sb.toString();
		}
		catch (Exception e)
		{
			userToken = RandomHelper.generateRandomString(8);
		}
		return userToken;
		
	}
	
	protected synchronized ChannelFuture getRemoteFuture()
	{
		if (null == remoteFuture || !remoteFuture.getChannel().isConnected())
		{
			ChannelPipeline pipeline = Channels.pipeline();
			// pipeline.addLast("executor", new ExecutionHandler(
			// SharedObjectHelper.getGlobalThreadPool()));
			pipeline.addLast("decoder", new HttpResponseDecoder());
			pipeline.addLast("encoder", new HttpRequestEncoder());
			pipeline.addLast("handler", new HttpResponseHandler());
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
			remoteFuture = getClientSocketChannelFactory().newChannel(pipeline)
			        .connect(new InetSocketAddress(connectHost, connectPort));
			
		}
		return remoteFuture;
	}
	
	protected abstract void doFinishTransaction();
	
	protected void onFullResponseReceived()
	{
	}
	
	protected void finishTransaction()
	{
		doFinishTransaction();
	}
	
	@Override
	public void run()
	{
		finishTransaction();
	}
	
	protected void fullResponseReceived()
	{
		waitingResponse.set(false);
		onFullResponseReceived();
	}
	
	class HttpResponseHandler extends SimpleChannelUpstreamHandler
	{
		private Buffer	cumulation;
		
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			waitingResponse.set(false);
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			logger.error("Push/Pull connection excetion caught:", e.getCause());
		}
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			Object obj = e.getMessage();
			if (obj instanceof HttpResponse)
			{
				HttpResponse response = (HttpResponse) obj;
				if(response.getStatus().getCode() != 200)
				{
					logger.error("Pull/Push received an error response:" + response);
					if(null != response.getContent())
					{
						int k = response.getContent().readableBytes();
						byte[] b = new byte[k];
						response.getContent().readBytes(b);
						logger.error("####Error cause:" + new String(b));
					}
					
					e.getChannel().close();
					return;
				}
				if (!response.isChunked())
				{
					fullResponseReceived();
				}
			}
			else if (obj instanceof HttpChunk)
			{
				HttpChunk chunk = (HttpChunk) obj;
				if (chunk.isLast())
				{
					fullResponseReceived();
				}
				else
				{
					ChannelBuffer content = chunk.getContent();
					byte[] tmp = new byte[content.readableBytes()];
					content.readBytes(tmp);
					
					if (null != cumulation && cumulation.readable())
					{
						cumulation.write(tmp);
						cumulation.discardReadedBytes();
					}
					else
					{
						cumulation = Buffer.wrapReadableContent(tmp);
					}
					if (cumulation.readableBytes() > 4)
					{
						int size = BufferHelper.readFixInt32(cumulation, true);
						if (size <= cumulation.readableBytes())
						{
							Buffer ck = Buffer.wrapReadableContent(
							        cumulation.getRawBuffer(),
							        cumulation.getReadIndex(), size);
							
							if (logger.isDebugEnabled())
							{
								logger.debug("Handle " + size + " chunk.");
							}
							conn.handleReceivedContent(ck);
							cumulation.advanceReadIndex(size);
						}
						else
						{
							cumulation.advanceReadIndex(-4);
						}
					}
				}
			}
		}
	}
}
