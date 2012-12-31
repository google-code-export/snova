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

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.spac.SPAC;

/**
 * @author yinqiwen
 * 
 */
public class ProxyHandler extends SimpleChannelUpstreamHandler implements
        LocalProxyHandler
{
	protected Logger	       logger	           = LoggerFactory
	                                                       .getLogger(getClass());
	
	private ProxyServerType	   serverType;
	private RemoteProxyManager	remoteProxyManager	= null;
	private RemoteProxyHandler	remoteHandler	   = null;
	private Channel	           localChannel	       = null;
	
	public Channel getLocalChannel()
	{
		return localChannel;
	}
	
	private Integer	             id;
	
	private static AtomicInteger	seed	= new AtomicInteger(1);
	
	public ProxyHandler(ProxyServerType type)
	{
		id = seed.getAndIncrement();
		serverType = type;
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
		else if (e instanceof ChannelBuffer)
		{
			if (null != remoteHandler)
			{
				remoteHandler.handleRawData(this, (ChannelBuffer) e);
			}
		}
		else
		{
			logger.error("Unsupported message type:" + e.getClass());
		}
	}
	
	private void handleHttpRequest(HttpRequest request)
	{
		RemoteProxyManager[] rm = SPAC.selectProxy(request, serverType);
		if (null == rm)
		{
			logger.error("No proxy service found.");
			close();
			return;
		}
		if (null == remoteProxyManager)
		{
			// only first handler support
			remoteProxyManager = rm[0];
			remoteHandler = remoteProxyManager.createProxyHandler();
		}
		else
		{
			if (!remoteProxyManager.getName().equalsIgnoreCase(rm[0].getName()))
			{
				remoteHandler.close();
				remoteProxyManager = rm[0];
				remoteHandler = remoteProxyManager.createProxyHandler();
			}
		}
		request.removeHeader("Proxy-Connection");
		remoteHandler.handleRequest(this, request);
	}
	
	private void doClose()
	{
		close();
		if (null != remoteHandler)
		{
			remoteHandler.close();
			remoteHandler = null;
		}
	}
	
	public void close()
	{
		if (localChannel != null)
		{
			if (localChannel.isConnected())
			{
				localChannel.close();
			}
			localChannel = null;
		}
		
	}
	
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
	        throws Exception
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Browser connection[" + id + "]  closed");
			localChannel = null;
		}
		doClose();
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
	        throws Exception
	{
		logger.error("Browser connection[" + id + "] exceptionCaught.",
		        e.getCause());
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
	        throws Exception
	{
		Object msg = e.getMessage();
		localChannel = ctx.getChannel();
		
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
	
	public void handleResponse(RemoteProxyHandler remote, HttpResponse res)
	{
		if (null != localChannel && localChannel.isConnected())
		{
			localChannel.write(res);
		}
		else
		{
			doClose();
		}
	}
	
	@Override
	public void handleChunk(RemoteProxyHandler remote, final HttpChunk chunk)
	{
		logger.info("Write chunk:" + chunk.getContent().readableBytes()
		        + localChannel);
		if (null != localChannel && localChannel.isConnected())
		{
			localChannel.write(chunk);
		}
		else
		{
			doClose();
		}
	}
	
	@Override
	public void handleRawData(RemoteProxyHandler remote, ChannelBuffer raw)
	{
		if (null != localChannel && localChannel.isConnected())
		{
			localChannel.write(raw);
		}
		else
		{
			doClose();
		}
	}
	
	@Override
	public int getId()
	{
		return id;
	}
	
	public void switchRawHandler()
	{
		if (null != localChannel)
		{
			// logger.info(String.format("Session[%d] switch raw handler", id));
			localChannel.getPipeline().remove("encoder");
			localChannel.getPipeline().remove("decoder");
		}
	}
}
