/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: XmppAccount.java 
 *
 * @author yinqiwen [ 2010-1-31 | 10:50:02 AM]
 *
 */
package org.snova.framework.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.RemoteProxyManagerHolder;

/**
 * @author yinqiwen
 * 
 */
public class ProxyHandler extends ChannelInboundMessageHandlerAdapter<Object>
        implements LocalProxyHandler
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	private RemoteProxyHandler remoteHandler = null;
	private Channel localChannel = null;

	public Channel getLocalChannel()
	{
		return localChannel;
	}

	private Integer id;

	private static AtomicInteger seed = new AtomicInteger(1);

	public ProxyHandler()
	{
		id = seed.getAndIncrement();
	}

	private void handleChunks(Object e)
	{
		if (e instanceof HttpChunk)
		{
			HttpChunk chunk = (HttpChunk) e;
			if (null != remoteHandler)
			{
				remoteHandler.handleChunk(this, chunk);
			}
		}
		else if (e instanceof ByteBuf)
		{
			if (null != remoteHandler)
			{
				remoteHandler.handleRawData(this, (ByteBuf) e);
			}
		}
		else
		{
			logger.error("Unsupported message type:" + e.getClass());
		}
	}

	private void handleHttpRequest(HttpRequest request)
	{
		if (null == remoteHandler)
		{
			String name = SnovaConfiguration.getInstance().getIniProperties()
			        .getProperty("SPAC", "Default", "GAE");
			RemoteProxyManager rm = RemoteProxyManagerHolder
			        .getRemoteProxyManager(name);
			if (null == rm)
			{
				logger.error("No proxy service:" + name + " found.");
				close();
				return;
			}
			remoteHandler = rm.createProxyHandler();
		}
		request.removeHeader("Proxy-Connection");
		remoteHandler.handleRequest(this, request);
	}

	public void close()
	{
		if (localChannel != null && localChannel.isActive())
		{
			localChannel.close();
			localChannel = null;
		}

	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Browser connection[" + id + "]  closed");
			localChannel = null;
		}
		close();
		if (null != remoteHandler)
		{
			remoteHandler.close();
			remoteHandler = null;
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	        throws Exception
	{
		logger.error("Browser connection[" + id + "] exceptionCaught.", cause);
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, Object msg)
	        throws Exception
	{
		localChannel = ctx.channel();

		if (msg instanceof HttpRequest)
		{
			HttpRequest request = (HttpRequest) msg;
			handleHttpRequest(request);
		}
		else
		{
			handleChunks(msg);
		}
	}

	@Override
	public void handleResponse(RemoteProxyHandler remote, HttpResponse res)
	{
		if (null != localChannel)
		{
			localChannel.write(res);
		}
		else
		{
			close();
		}
	}

	@Override
	public void handleChunk(RemoteProxyHandler remote, final HttpChunk chunk)
	{
		logger.info("Write chunk:" + chunk.getContent().readableBytes()
		        + localChannel);
		if (null != localChannel)
		{
			localChannel.write(chunk);
		}
		else
		{
			close();
		}
	}

	@Override
	public void handleRawData(RemoteProxyHandler remote, ByteBuf raw)
	{
		if (null != localChannel)
		{
			localChannel.write(raw);
		}
		else
		{
			close();
		}
	}

	@Override
	public int getId()
	{
		return id;
	}
}
