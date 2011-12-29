/**
 * 
 */
package org.snova.heroku.client.connection;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.write;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.common.Pair;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelDownstreamHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.FrameDecoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.heroku.client.config.HerokuClientConfiguration;
import org.snova.heroku.client.config.HerokuClientConfiguration.HerokuServerAuth;
import org.snova.heroku.common.event.HerokuRawSocketEvent;

/**
 * @author qiyingwang
 * 
 */
public class RawProxyConnection extends ProxyConnection implements Runnable
{
	private static Channel server;

	private static Map<String, Pair<Channel, RawProxyConnection>> connectedChannelTable = new HashMap<String, Pair<Channel, RawProxyConnection>>();

	private static void initServer()
	{
		if (null == server)
		{
			Executor bossExecutor = Executors.newCachedThreadPool();
			final Executor workerExecutor = SharedObjectHelper
			        .getGlobalThreadPool();
			NioServerSocketChannelFactory serverSocketFactory = new NioServerSocketChannelFactory(
			        bossExecutor, workerExecutor);
			ChannelPipeline pipeline = pipeline();
			//pipeline.addLast("executor", new ExecutionHandler(workerExecutor));
			pipeline.addLast("decoder", new HerokuRawEventDecoder());
			pipeline.addLast("encoder", new HerokuRawEventEncoder());
			pipeline.addLast("handler", new HerokuRawEventHandler());
			server = serverSocketFactory.newChannel(pipeline);
			server.getConfig().setPipelineFactory(new ChannelPipelineFactory()
			{
				@Override
				public ChannelPipeline getPipeline() throws Exception
				{
					// Create a default pipeline implementation.
					ChannelPipeline pipeline = pipeline();
					//pipeline.addLast("executor", new ExecutionHandler(
					//        workerExecutor));
					pipeline.addLast("decoder", new HerokuRawEventDecoder());
					pipeline.addLast("encoder", new HerokuRawEventEncoder());
					pipeline.addLast("handler", new HerokuRawEventHandler());
					return pipeline;
				}
			});
			SimpleSocketAddress addr = HerokuClientConfiguration.getInstance()
			        .getRSocketServerAddress();
			addr.host = "0.0.0.0";
			server.bind(new InetSocketAddress(addr.host, addr.port));

		}
	}

	private static synchronized Channel getConnectedChannel(String domain)
	{
		Pair<Channel, RawProxyConnection> pair = connectedChannelTable
		        .get(domain);
		if (null != pair)
		{
			if (pair.first != null)
			{
				if (!pair.first.isConnected())
				{
					pair.first = null;
				}
			}
			return pair.first;
		}
		return null;
	}

	private static synchronized RawProxyConnection getConnectedRawProxyConnection(
	        String domain)
	{
		Pair<Channel, RawProxyConnection> pair = connectedChannelTable
		        .get(domain);
		if (null != pair)
		{
			return pair.second;
		}
		return null;
	}

	private static synchronized void saveConnectedChannel(String domain,
	        Channel ch)
	{
		Pair<Channel, RawProxyConnection> pair = connectedChannelTable
		        .get(domain);
		if (null == pair)
		{
			pair = new Pair<Channel, RawProxyConnection>(ch, null);
			connectedChannelTable.put(domain, pair);
		}
		pair.first = ch;
	}

	private static synchronized void saveRawProxyConnection(String domain,
	        RawProxyConnection ch)
	{
		Pair<Channel, RawProxyConnection> pair = connectedChannelTable
		        .get(domain);
		if (null == pair)
		{
			pair = new Pair<Channel, RawProxyConnection>(null, ch);
			connectedChannelTable.put(domain, pair);
		}
		pair.second = ch;
	}

	public RawProxyConnection(HerokuServerAuth auth)
	{
		super(auth);
		initServer();
		SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(this, 1, 1,
		        TimeUnit.SECONDS);
		saveRawProxyConnection(auth.domain, this);
	}

	@Override
	protected boolean doSend(Buffer msgbuffer)
	{
		HerokuRawSocketEvent ev = new HerokuRawSocketEvent(auth.domain,
		        msgbuffer);
		Channel ch = getConnectedChannel(auth.domain);
		if (ch == null)
		{
			return false;
		}
		ch.write(ev);
		return true;
	}

	@Override
	protected int getMaxDataPackageSize()
	{
		return 0;
	}

	@Override
	public boolean isReady()
	{
		Channel ch = getConnectedChannel(auth.domain);
		return ch != null && ch.isConnected();
	}

	@ChannelPipelineCoverage("one")
	static class HerokuRawEventEncoder extends SimpleChannelDownstreamHandler
	{
		@Override
		public void writeRequested(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			if (e.getMessage() instanceof HerokuRawSocketEvent)
			{
				HerokuRawSocketEvent ev = (HerokuRawSocketEvent) e.getMessage();
				Buffer buffer = new Buffer(1024);
				ev.encode(buffer);
				ChannelBuffer encodedMessage = ChannelBuffers.wrappedBuffer(
				        buffer.getRawBuffer(), 0, buffer.readableBytes());
				write(ctx, e.getFuture(), encodedMessage);
			}
			else
			{
				logger.error("Not supported write message type:"
				        + e.getMessage().getClass().getName());
			}
		}
	}

	@ChannelPipelineCoverage("one")
	static class HerokuRawEventDecoder extends FrameDecoder
	{
		private int getInt(ChannelBuffer buf)
		{
			byte[] temp = new byte[4];
			buf.readBytes(temp);
			Buffer buffer = Buffer.wrapReadableContent(temp);
			return BufferHelper.readFixInt32(buffer, true);

		}

		private HerokuRawSocketEvent decodeHerokuRawSocketEvent(
		        ChannelBuffer buf)
		{
			logger.error("Enter with rest:" + buf.readableBytes() + " at thread:" + Thread.currentThread().getName());
			HerokuRawSocketEvent ev = new HerokuRawSocketEvent(null, null);
			//int currentRead = buf.readerIndex();
			//int currentWrite = buf.writerIndex();
			buf.markReaderIndex();
			if (buf.readableBytes() <= 4)
			{
				logger.error("Exit with rest:" + buf.readableBytes()+ " at thread:" + Thread.currentThread().getName());
				return null;
			}

			int size = getInt(buf);
			
			if (buf.readableBytes() < size)
			{
				buf.resetReaderIndex();
				logger.error("Exit with rest:" + buf.readableBytes()+ " at thread:" + Thread.currentThread().getName());
				return null;
			}
			byte[] dst = new byte[size];
			buf.readBytes(dst);
			ev.domain = new String(dst);
			if (buf.readableBytes() <= 4)
			{
				buf.resetReaderIndex();
				logger.error("Exit with rest:" + buf.readableBytes()+ " at thread:" + Thread.currentThread().getName());
				return null;
			}
			size = getInt(buf);
			if (buf.readableBytes() < size)
			{
				buf.resetReaderIndex();
				logger.error("Exit with rest:" + buf.readableBytes()+ " at thread:" + Thread.currentThread().getName());
				return null;
			}
			byte[] b = new byte[size];
			buf.readBytes(b);
			ev.content = Buffer.wrapReadableContent(b);
			logger.error("Exit with rest:" + buf.readableBytes()+ " at thread:" + Thread.currentThread().getName());
			return ev;
		}

		@Override
		protected Object decode(ChannelHandlerContext ctx, Channel channel,
		        ChannelBuffer buffer) throws Exception
		{
			return decodeHerokuRawSocketEvent(buffer);
		}
	}

	@ChannelPipelineCoverage("one")
	static class HerokuRawEventHandler extends SimpleChannelUpstreamHandler
	{
		@Override
		public void channelConnected(ChannelHandlerContext ctx,
		        ChannelStateEvent e) throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Rsocket connected");
			}
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
		        throws Exception
		{
			ctx.getChannel().close();
			logger.error("Caught exception:", e.getCause());
		}

		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
		        throws Exception
		{
			if(logger.isDebugEnabled())
			{
				logger.debug("Received mesage:" + e.getMessage().getClass().getName());
			}
			if (e.getMessage() instanceof HerokuRawSocketEvent)
			{
				HerokuRawSocketEvent ev = (HerokuRawSocketEvent) e.getMessage();
				saveConnectedChannel(ev.domain, ctx.getChannel());
				if (ev.content.readable())
				{
					RawProxyConnection conn = getConnectedRawProxyConnection(ev.domain);
					if (null != conn)
					{
						conn.doRecv(ev.content);
					}
					else
					{
						logger.error("No proxy conn found to handle raw event.");
					}
				}
			}
			else
			{
				logger.error("Not supported message type:"
				        + e.getMessage().getClass().getName());
			}
		}
	}

	private void ping()
	{
		try
		{
			SimpleSocketAddress addr = HerokuClientConfiguration.getInstance()
			        .getRSocketServerAddress();
			String ip = InetAddress.getLocalHost().getHostAddress();
			String hb = "POST http://"
			        + auth.domain
			        + "/raw HTTP/1.1\r\n"
			        + "Host: "
			        + auth.domain
			        + "\r\n"
			        + "Connection: close\r\n"
			        + "User-Agent: Mozilla/5.0 (Windows NT 5.1) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.220 Safari/535.1\r\n"
			        + "X-HerokuAuth: " + auth.domain + "-" + ip + "-"
			        + addr.port + "\r\n" + "Content-Length: 0\r\n\r\n";

			Socket s = new Socket(auth.domain, 8080);
			s.getOutputStream().write(hb.getBytes());
			byte[] b = new byte[256];
			s.getInputStream().read(b);
			s.close();
		}
		catch (Exception e)
		{
			logger.error("Failed", e);
		}
	}

	@Override
	public void run()
	{
		ping();
	}
}
