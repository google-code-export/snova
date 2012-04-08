/**
 * 
 */
package org.snova.c4.client.connection.rsocket;

import static org.jboss.netty.channel.Channels.pipeline;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
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
	protected static NioServerSocketChannelFactory serverFactory = null;
	protected static ServerSocketChannel rserver = null;
	protected static Map<C4ServerAuth, RSocketProxyConnection> rSocketProxyConnectionTable = new ConcurrentHashMap<C4ServerAuth, RSocketProxyConnection>();
	protected Channel acceptedRemoteChannel;

	public RSocketProxyConnection(C4ServerAuth auth)
	{
		super(auth);
		init();
	}

	static RSocketProxyConnection saveRConnection(C4ServerAuth auth,
	        Channel channel)
	{
		RSocketProxyConnection conn = rSocketProxyConnectionTable.get(auth);
		conn.acceptedRemoteChannel = channel;
		return conn;
	}

	void closeRConnection()
	{
		acceptedRemoteChannel = null;
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
		acceptedRemoteChannel.write(msg);
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
		return null != acceptedRemoteChannel
		        && acceptedRemoteChannel.isConnected();
	}

	private void heartBeat()
	{
		try
		{
			String urlstr = "http://" + auth.domain;
			if (auth.port != 80)
			{
				urlstr = urlstr + ":" + auth.port;
			}
			urlstr = urlstr + "/rsocket";
			URL url = new URL(urlstr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty(C4Constants.USER_TOKEN_HEADER,
			        ConnectionHelper.getUserToken());
			String extenral = C4ClientConfiguration.getInstance()
			        .getExternalIP();
			if (null == extenral)
			{
				extenral = GeneralNetworkHelper.getPublicIP();
			}
			conn.setRequestProperty(C4Constants.LOCAL_RSERVER_ADDR_HEADER,
			        extenral
			                + ":"
			                + C4ClientConfiguration.getInstance()
			                        .getRServerPort());
			conn.setRequestMethod("POST");
			conn.connect();
			int responseCode = conn.getResponseCode();
			if (responseCode != 200)
			{
				logger.error("RSocket heart beat request request failed with response code:"
				        + responseCode);
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
