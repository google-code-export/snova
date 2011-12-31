/**
 * 
 */
package org.snova.heroku.client.connection;

import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.write;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.common.Pair;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.util.ListSelector;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
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
import org.snova.heroku.client.handler.ProxySessionManager;
import org.snova.heroku.common.codec.HerokuRawSocketEventFrameDecoder;
import org.snova.heroku.common.event.HerokuRawSocketEvent;

/**
 * @author qiyingwang
 * 
 */
public class RawProxyConnection extends ProxyConnection implements Runnable
{
	private static Channel server;
	private static ServerSocket serverSocket;

	static class ReverseConnection
	{
		RawProxyConnection logicConnection;
		ListSelector<Channel> rawConnections = new ListSelector<Channel>();
	}
	
	private static Map<String, ReverseConnection> connectedChannelTable = new HashMap<String, ReverseConnection>();
	private static Map<String, Pair<Socket, RawProxyConnection>> connectedSocketTable = new HashMap<String, Pair<Socket, RawProxyConnection>>();

	private ChannelFuture writeFuture;
	// private static boolean initServer2()
	// {
	// if(null == serverSocket)
	// {
	// try
	// {
	// serverSocket = new ServerSocket();
	// SimpleSocketAddress addr = HerokuClientConfiguration.getInstance()
	// .getRSocketServerAddress();
	// addr.host = "0.0.0.0";
	// serverSocket.bind(new InetSocketAddress(addr.host, addr.port));
	// new Thread(new ServerSocketTask(serverSocket)).start();
	// return true;
	// }
	// catch (Exception e)
	// {
	// logger.error("Failed to init RSocket Server.", e);
	// return false;
	// }
	// }
	// return true;
	// }

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
			pipeline.addLast("executor", new ExecutionHandler(workerExecutor));
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
					pipeline.addLast("executor", new ExecutionHandler(
					        workerExecutor));
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
		ReverseConnection pair = connectedChannelTable
		        .get(domain);
		if (null != pair)
		{
			return pair.rawConnections.select();
		}
		return null;
	}

	private static synchronized RawProxyConnection getConnectedRawProxyConnection(
	        String domain)
	{
		ReverseConnection pair = connectedChannelTable
		        .get(domain);
		if (null != pair)
		{
			return pair.logicConnection;
		}
		return null;
	}

	private static synchronized void saveConnectedChannel(String domain,
	        Channel ch)
	{
		ReverseConnection pair = connectedChannelTable
		        .get(domain);
		if (null == pair)
		{
			pair = new ReverseConnection();
			connectedChannelTable.put(domain, pair);
		}
		pair.rawConnections.add(ch);
	}

	private static synchronized void saveRawProxyConnection(String domain,
	        RawProxyConnection ch)
	{
		ReverseConnection pair = connectedChannelTable
		        .get(domain);
		if (null == pair)
		{
			pair = new ReverseConnection();
			connectedChannelTable.put(domain, pair);
		}
		pair.logicConnection = ch;
	}

	// private static synchronized Socket getConnectedSocket(String domain)
	// {
	// Pair<Socket, RawProxyConnection> pair = connectedSocketTable
	// .get(domain);
	// if (null != pair)
	// {
	// if (pair.first != null)
	// {
	// if (!pair.first.isConnected())
	// {
	// pair.first = null;
	// }
	// }
	// return pair.first;
	// }
	// return null;
	// }
	//
	// private static synchronized RawProxyConnection
	// getConnectedRawProxyConnection(
	// String domain)
	// {
	// Pair<Socket, RawProxyConnection> pair = connectedSocketTable
	// .get(domain);
	// if (null != pair)
	// {
	// return pair.second;
	// }
	// return null;
	// }
	//
	// private static synchronized void saveConnectedSocket(String domain,
	// Socket ch)
	// {
	// Pair<Socket, RawProxyConnection> pair = connectedSocketTable
	// .get(domain);
	// if (null == pair)
	// {
	// pair = new Pair<Socket, RawProxyConnection>(ch, null);
	// connectedSocketTable.put(domain, pair);
	// }
	// pair.first = ch;
	// }
	//
	// private static synchronized void saveRawProxyConnection(String domain,
	// RawProxyConnection ch)
	// {
	// Pair<Socket, RawProxyConnection> pair = connectedSocketTable
	// .get(domain);
	// if (null == pair)
	// {
	// pair = new Pair<Socket, RawProxyConnection>(null, ch);
	// connectedSocketTable.put(domain, pair);
	// }
	// pair.second = ch;
	// }

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
		writeFuture = ch.write(ev);
		// Socket client = getConnectedSocket(auth.domain);
		// if (client == null)
		// {
		// return false;
		// }
		// Buffer content = new Buffer(msgbuffer.readableBytes() + 100);
		// ev.encode(content);
		// try
		// {
		// client.getOutputStream().write(content.getRawBuffer(), 0,
		// content.readableBytes());
		// }
		// catch (IOException e)
		// {
		// logger.error("Failed to send content.", e);
		// return false;
		// }
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
		return ch != null;
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
			HerokuRawSocketEvent ev = new HerokuRawSocketEvent(null, null);
			// int currentRead = buf.readerIndex();
			// int currentWrite = buf.writerIndex();
			buf.markReaderIndex();
			if (buf.readableBytes() <= 4)
			{
				return null;
			}

			// int size = getInt(buf);
			int size = buf.readInt();
			if (buf.readableBytes() < size)
			{
				buf.resetReaderIndex();
				return null;
			}
			byte[] dst = new byte[size];
			buf.readBytes(dst);
			ev.domain = new String(dst);
			if (buf.readableBytes() <= 4)
			{
				buf.resetReaderIndex();
				return null;
			}
			// size = getInt(buf);
			size = buf.readInt();
			if (buf.readableBytes() < size)
			{
				buf.resetReaderIndex();
				return null;
			}
			byte[] b = new byte[size];
			buf.readBytes(b);
			ev.content = Buffer.wrapReadableContent(b);
			return ev;
		}

		@Override
		protected Object decode(ChannelHandlerContext ctx, Channel channel,
		        ChannelBuffer buffer) throws Exception
		{
			if (logger.isDebugEnabled())
			{
				logger.debug("Enter decode with buffer size:"
				        + buffer.readableBytes());
			}
			HerokuRawSocketEvent ev = decodeHerokuRawSocketEvent(buffer);
			return ev;
		}
	}

	@ChannelPipelineCoverage("one")
	static class HerokuRawEventHandler extends SimpleChannelUpstreamHandler
	{

		private String dm;
		@Override
		public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
		        throws Exception
		{
			ReverseConnection conn = connectedChannelTable.get(dm);
			if(null != conn)
			{
				conn.rawConnections.remove(ctx.getChannel());
			}
		}

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
			if (logger.isDebugEnabled())
			{
				logger.debug("Received mesage:"
				        + e.getMessage().getClass().getName());
			}
			if (e.getMessage() instanceof HerokuRawSocketEvent)
			{
				HerokuRawSocketEvent ev = (HerokuRawSocketEvent) e.getMessage();
				saveConnectedChannel(ev.domain, ctx.getChannel());
				dm = ev.domain;
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

			ProxySessionManager.getInstance().run();
			send((Event)null);
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

	// static class ClientSocketTask implements Runnable
	// {
	// Socket client;
	//
	// public ClientSocketTask(Socket client)
	// {
	// this.client = client;
	// }
	//
	// @Override
	// public void run()
	// {
	// HerokuRawSocketEventFrameDecoder decoder = new
	// HerokuRawSocketEventFrameDecoder();
	// byte[] readBuffer = new byte[8192];
	// while (true)
	// {
	// try
	// {
	// int len = client.getInputStream().read(readBuffer);
	// logger.info("Receve data:" + len);
	// if(len > 0)
	// {
	// Buffer content = Buffer.wrapReadableContent(readBuffer, 0, len);
	// while(content.readable())
	// {
	// HerokuRawSocketEvent ev = decoder.decode(content);
	//
	// if(null == ev)
	// {
	// logger.info("Rest buf size:" + decoder.cumulationSize());
	// break;
	// }
	// saveConnectedSocket(ev.domain, client);
	// final RawProxyConnection conn =
	// getConnectedRawProxyConnection(ev.domain);
	// conn.doRecv(ev.content);
	// }
	// }
	// }
	// catch (Exception e)
	// {
	// logger.error("Failed to handle client socket.", e);
	// break;
	// }
	// }
	//
	// }
	//
	// }
	//
	// static class ServerSocketTask implements Runnable
	// {
	// private ServerSocket serverSocket;
	//
	// public ServerSocketTask(ServerSocket serverSocket)
	// {
	// this.serverSocket = serverSocket;
	// }
	//
	// @Override
	// public void run()
	// {
	// while (true)
	// {
	// try
	// {
	// Socket client = serverSocket.accept();
	// SharedObjectHelper.getGlobalThreadPool().submit(
	// new ClientSocketTask(client));
	// }
	// catch (IOException e)
	// {
	// logger.error("Failed to handle server socket.", e);
	// }
	//
	// }
	//
	// }
	//
	// }
}
