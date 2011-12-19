/**
 * 
 */
package org.snova.spac.session;

import java.io.IOException;
import java.net.UnknownHostException;

import net.sourceforge.jsocks.socks.Proxy;
import net.sourceforge.jsocks.socks.Socks4Proxy;
import net.sourceforge.jsocks.socks.Socks5Proxy;
import net.sourceforge.jsocks.socks.SocksException;
import net.sourceforge.jsocks.socks.SocksSocket;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 * 
 */
public class SocksSession extends Session implements Runnable
{
	protected static Logger logger = LoggerFactory
	        .getLogger(SocksSession.class);
	private SocksSocket client;
	private Proxy socksproxy;

	public SocksSession(String target) throws UnknownHostException
	{
		String[] ss = target.split(":");
		if (ss.length != 3)
		{
			logger.error("Invalid Socks proxy setting!");
			throw new UnknownHostException("Invalid socks proxy address!");
		}
		String protocol = ss[0].trim();
		String host = ss[1].trim();
		int port = Integer.parseInt(ss[2].trim());
		setSocksProxy(protocol, host, port);
	}

	protected void setSocksProxy(String protocol, String host, int port)
	        throws UnknownHostException
	{
		if (protocol.equalsIgnoreCase("socks5"))
		{
			socksproxy = new Socks5Proxy(host, port);
			((Socks5Proxy) socksproxy).resolveAddrLocally(false);
		}
		else if (protocol.equalsIgnoreCase("socks4"))
		{
			socksproxy = new Socks4Proxy(host, port, "");
		}
		else
		{
			throw new UnknownHostException("Invalid protocol");
		}
	}

	@Override
	public SessionType getType()
	{
		return SessionType.SOCKS;
	}

	private boolean writeRemoteSocket(Buffer buffer)
	{
		if (null != client)
		{
			try
			{
				client.getOutputStream().write(buffer.getRawBuffer(),
				        buffer.getReadIndex(), buffer.readableBytes());
				// client.getOutputStream().flush();
			}
			catch (IOException e)
			{
				logger.error("Failed to write socks proxy client.");
				return false;
			}
		}
		return true;
	}

	private boolean writeRemoteSocket(byte[] buffer)
	{
		if (null != client)
		{
			try
			{
				client.getOutputStream().write(buffer);
				// client.getOutputStream().flush();
			}
			catch (IOException e)
			{
				logger.error("Failed to write socks proxy client.");
				return false;
			}

		}
		return true;
	}

	

	@Override
	public void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) event;
				SimpleSocketAddress addr = getRemoteAddressFromRequestEvent(req);
				if (null == client)
				{
					try
					{
						client = new SocksSocket(socksproxy, addr.host,
						        addr.port);
					}
					catch (Exception e)
					{
						logger.error("Failed to create SocksSocket:" + addr, e);
						HttpResponse res = new DefaultHttpResponse(
						        HttpVersion.HTTP_1_1,
						        HttpResponseStatus.SERVICE_UNAVAILABLE);
						localChannel.write(res);
						// closeLocalChannel();
						return;
					}

					if (logger.isDebugEnabled())
					{
						logger.debug("Create a socks proxy socket fore remote:"
						        + addr);
					}
					SharedObjectHelper.getGlobalThreadPool().submit(this);
				}
				if (req.method.equalsIgnoreCase("Connect"))
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
					ChannelFuture future = localChannel.write(res);
					removeCodecHandler(localChannel, future);
					return;
				}
				else
				{
					Buffer buf = buildRequestBuffer(req);
					writeRemoteSocket(buf);
				}
				break;
			}
			case HTTPEventContants.HTTP_CHUNK_EVENT_TYPE:
			{
				HTTPChunkEvent chunk = (HTTPChunkEvent) event;
				if (null != client)
				{
					writeRemoteSocket(chunk.content);
				}
				else
				{
					logger.error("Null socks client to handler chunk event.");
				}
				break;
			}
			case HTTPEventContants.HTTP_CONNECTION_EVENT_TYPE:
			{
				HTTPConnectionEvent ev = (HTTPConnectionEvent) event;
				if (ev.status == HTTPConnectionEvent.CLOSED)
				{
					closeSocksClient();
				}
				break;
			}
			default:
			{
				logger.error("Unexpected event type:" + header.type);
				break;
			}
		}

	}

	private void closeSocksClient()
	{
		if (null != client)
		{
			try
			{
				client.close();
			}
			catch (IOException e)
			{
				logger.error("Failed to close socks client.", e);
			}
			client = null;
		}
	}

	@Override
	public void run()
	{
		byte[] buf = new byte[8192];
		while (true)
		{
			try
			{
				int ret = client.getInputStream().read(buf);
				if (ret > 0)
				{
					localChannel
					        .write(ChannelBuffers.copiedBuffer(buf, 0, ret));
				}
				else
				{
					if (ret < 0)
					{
						logger.error("Recv none bytes:" + ret);
						break;
					}
				}
			}
			catch (IOException e)
			{
				logger.error("Failed to read socks client.", e);
				break;
			}
		}
		// closeLocalChannel();
		closeSocksClient();

	}

}
