/**
 * 
 */
package org.snova.framework.proxy.c4.ws;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.config.IniProperties;
import org.arch.encrypt.RC4;
import org.arch.misc.crypto.base64.Base64;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.CumulateReader;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author yinqiwen
 * 
 */
public class IOWorker extends SimpleChannelUpstreamHandler
{
	WSTunnelService	       serv;
	int	                   index;
	ChannelFuture	       conn;
	Buffer	               unsentContent	= new Buffer();
	boolean	               connected;
	private CumulateReader	cumulater	 = new CumulateReader();
	
	public IOWorker(WSTunnelService wsTunnelService, int i)
	{
		this.serv = wsTunnelService;
		this.index = i;
	}
	
	synchronized void write(Buffer buf)
	{
		unsentContent.write(buf, buf.readableBytes());
		if (connected)
		{
			tryWriteCache();
		}
		else
		{
			System.out.println("#######Not connected!");
		}
	}
	
	private synchronized void tryWriteCache()
	{
		if (connected && unsentContent.readable())
		{
			byte[] tmp = new byte[unsentContent.readableBytes()];
			unsentContent.read(tmp);
			unsentContent.clear();
			conn.getChannel().write(ChannelBuffers.wrappedBuffer(tmp));
		}
	}
	
	public void start()
	{
		if (null != conn && conn.getChannel().isConnected())
		{
			return;
		}
		connected = false;
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		StringBuilder buffer = new StringBuilder();
		buffer.append("GET ").append(serv.server.url.getPath())
		        .append("HTTP/1.1\r\n");
		buffer.append("Upgrade: WebSocket\r\n");
		String enc = cfg.getProperty("C4", "Encrypter", "RC4");
		if (enc.equalsIgnoreCase("RC4"))
		{
			String key = cfg.getProperty("Misc", "RC4Key");
			RC4.setDefaultKey(key);
			byte[] tmp = RC4.encrypt(key.getBytes());
			buffer.append("RC4Key:").append(Base64.encodeToString(tmp, false)).append("\r\n");
		}
		String host = serv.server.url.getHost();
		int port = serv.server.url.getPort();
		if (port < 0)
		{
			port = 80;
		}
		buffer.append("Host: ").append(host).append(":").append(port)
		        .append("\r\n");
		buffer.append("Connection: Upgrade\r\n");
		buffer.append("ConnectionIndex:").append(index).append("\r\n");
		buffer.append("UserToken:").append(NetworkHelper.getMacAddress())
		        .append("\r\n");
		buffer.append("Keep-Alive:")
		        .append(cfg.getIntProperty("C4", "WSConnKeepAlive", 300))
		        .append("\r\n\r\n");
		final ChannelBuffer request = ChannelBuffers.wrappedBuffer(buffer
		        .toString().getBytes());
		
		conn = SharedObjectHelper.getClientBootstrap().connect(
		        new InetSocketAddress(host, port));
		conn.getChannel().getPipeline()
		        .addLast("codec", new HttpResponseDecoder());
		conn.getChannel().getPipeline().addLast("handler", this);
		conn.addListener(new ChannelFutureListener()
		{
			public void operationComplete(ChannelFuture future)
			        throws Exception
			{
				if (future.isSuccess())
				{
					future.getChannel().write(request);
				}
			}
		});
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
	        throws Exception
	{
		
		if (!connected)
		{
			HttpResponse res = (HttpResponse) e.getMessage();
			System.out.println("####recv " + res);
			if (res.getStatus().getCode() == 101)
			{
				conn.getChannel().getPipeline().remove("codec");
				connected = true;
				tryWriteCache();
			}
			else
			{
				conn.getChannel().close();
				SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
				{
					public void run()
					{
						start();
					}
				}, 1, TimeUnit.SECONDS);
			}
		}
		else
		{
			ChannelBuffer content = (ChannelBuffer) e.getMessage();
			cumulater.fillResponseBuffer(content);
		}
		
	}
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
	        throws Exception
	{
		
		if (connected)
		{
			connected = false;
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			{
				public void run()
				{
					start();
				}
			}, 100, TimeUnit.MILLISECONDS);
		}
	}
}
