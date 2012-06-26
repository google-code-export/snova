/**
 * 
 */
package org.snova.c4.client.connection.rsocket;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.util.ListSelector;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.LengthFieldBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.LengthFieldPrepender;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.connection.ProxyConnection;
import org.snova.c4.client.connection.util.ConnectionHelper;
import org.snova.c4.common.C4Constants;
import org.snova.framework.util.GeneralNetworkHelper;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 * 
 */
public class RSocketProxyConnection extends ProxyConnection implements Runnable
{
	protected static NioServerSocketChannelFactory	           serverFactory	            = null;
	protected static ServerSocketChannel	                   rserver	                    = null;
	protected static Map<C4ServerAuth, RSocketProxyConnection>	rSocketProxyConnectionTable	= new ConcurrentHashMap<C4ServerAuth, RSocketProxyConnection>();
	protected ListSelector<Channel>	                           acceptedRemoteChannels	    = new ListSelector<Channel>();
	
	public RSocketProxyConnection(C4ServerAuth auth)
	{
		super(auth);
		init();
	}
	
	static RSocketProxyConnection saveRConnection(C4ServerAuth auth,
	        Channel channel)
	{
		RSocketProxyConnection conn = rSocketProxyConnectionTable.get(auth);
		conn.acceptedRemoteChannels.add(channel);
		return conn;
	}
	
	void closeRConnection(Channel ch)
	{
		acceptedRemoteChannels.remove(ch);
	}
	
	void handleEvent(Event ev)
	{
		handleRecvEvent(ev);
	}
	
	protected static NioServerSocketChannelFactory getServerFactory()
	{
		if (null == serverFactory)
		{
			serverFactory = new NioServerSocketChannelFactory(
			        SharedObjectHelper.getGlobalThreadPool(),
			        SharedObjectHelper.getGlobalThreadPool());
		}
		return serverFactory;
	}
	
	private void init()
	{
		if (null == rserver)
		{
			rserver = getServerFactory().newChannel(Channels.pipeline());
			rserver.getConfig().setPipelineFactory(new ChannelPipelineFactory()
			{
				@Override
				public ChannelPipeline getPipeline() throws Exception
				{
					// Create a default pipeline implementation.
					ChannelPipeline pipeline = pipeline();
					pipeline.addLast("decoder",
					        new LengthFieldBasedFrameDecoder(1024 * 1024 * 10,
					                0, 4, 0, 4));
					pipeline.addLast("encoder", new LengthFieldPrepender(4));
					pipeline.addLast("handler", new RSocketHandler());
					return pipeline;
				}
			});
			rserver.bind(new InetSocketAddress(C4ClientConfiguration
			        .getInstance().getRServerPort()));
		}
		rSocketProxyConnectionTable.put(auth, this);
		heartBeat();
		int period = C4ClientConfiguration.getInstance().getHeartBeatPeriod();
		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, period,
		        period, TimeUnit.MILLISECONDS);
//		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(new Runnable()
//		{
//			public void run()
//			{
//				for (int i = 0; i < acceptedRemoteChannels.size(); i++)
//				{
//					acceptedRemoteChannels.select().write(
//					        ChannelBuffers.EMPTY_BUFFER);
//				}
//			}
//		}, 1, 1, TimeUnit.SECONDS);
	}
	
	@Override
	protected boolean doSend(Buffer msgbuffer)
	{
		if (!isReady())
		{
			return false;
		}
		ChannelBuffer msg = ChannelBuffers.wrappedBuffer(
		        msgbuffer.getRawBuffer(), msgbuffer.getReadIndex(),
		        msgbuffer.readableBytes());
		acceptedRemoteChannels.select().write(msg);
		return true;
	}
	
	@Override
	protected int getMaxDataPackageSize()
	{
		return -1;
	}
	
	@Override
	public boolean isReady()
	{
		return acceptedRemoteChannels.size() > 0;
	}
	
	private void heartBeat()
	{
		try
		{
			Socket client = new Socket(auth.domain, auth.port);
			String extenral = C4ClientConfiguration.getInstance()
			        .getExternalIP();
			if (null == extenral)
			{
				extenral = GeneralNetworkHelper.getPublicIP();
			}
			StringBuilder buffer = new StringBuilder();
			buffer.append("GET /rsocket HTTP/1.1\r\n");
			buffer.append("HOST: ").append(auth.domain).append("\r\n");
			buffer.append(C4Constants.USER_TOKEN_HEADER).append(": ")
			        .append(ConnectionHelper.getUserToken()).append("\r\n");
			buffer.append(C4Constants.LOCAL_RSERVER_ADDR_HEADER)
			        .append(": ")
			        .append(extenral
			                + "_"
			                + C4ClientConfiguration.getInstance()
			                        .getRServerPort()
			                + "_"
			                + C4ClientConfiguration.getInstance()
			                        .getConnectionPoolSize() + "\r\n");
			buffer.append("Connection: close\r\n");
			buffer.append("Content-Length: 0\r\n\r\n");
			logger.info(buffer.toString());
			client.getOutputStream().write(buffer.toString().getBytes());
			byte[] resbuf = new byte[1024];
			int len = client.getInputStream().read(resbuf);
			if (len > 0)
			{
				logger.info("RSocket heart beat request  with response:"
				        + new String(resbuf, 0, len));
			}
			
		}
		catch (Exception e)
		{
			logger.error("Failed to get remote hosts file.", e);
		}
	}
	
	@Override
	public void run()
	{
		heartBeat();
	}
	
}
