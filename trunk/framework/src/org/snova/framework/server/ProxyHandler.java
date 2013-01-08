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
	protected Logger	         logger	                  = LoggerFactory
	                                                              .getLogger(getClass());
	
	private ProxyServerType	     serverType;
	private RemoteProxyManager[]	candidateProxyManager	= null;
	private String[]	         proxyAttr;
	private RemoteProxyHandler	 remoteHandler	          = null;
	private Channel	             localChannel	          = null;
	
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
	
	private void processHttpRequest(RemoteProxyManager rm, HttpRequest request)
	{
		remoteHandler = rm.createProxyHandler(proxyAttr);
		remoteHandler.handleRequest(this, request);
	}
	
	private void handleHttpRequest(HttpRequest request)
	{
		request.removeHeader("Proxy-Connection");
		Object[] attr = new Object[1];
		candidateProxyManager = SPAC.selectProxy(request, serverType, attr);
		if (null == candidateProxyManager || candidateProxyManager.length == 0)
		{
			logger.error("No proxy service found for "
			        + request.getHeader("Host"));
			close();
			return;
		}
		proxyAttr = (String[]) attr[0];
		if (remoteHandler != null)
		{
			boolean match = false;
			for (RemoteProxyManager rm : candidateProxyManager)
			{
				if (rm.getName().equalsIgnoreCase(remoteHandler.getName()))
				{
					match = true;
					break;
				}
			}
			if (match)
			{
				remoteHandler.handleRequest(this, request);
				return;
			}
			else
			{
				remoteHandler.close();
			}
		}
		processHttpRequest(candidateProxyManager[0], request);
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
			if (localChannel.isOpen())
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
			localChannel.getPipeline().remove("encoder");
			localChannel.getPipeline().remove("decoder");
		}
	}
	
	@Override
	public void onProxyFailed(RemoteProxyHandler remote,
	        HttpRequest proxyRequest)
	{
		if (null != candidateProxyManager)
		{
			for (int i = 0; i < candidateProxyManager.length; i++)
			{
				if (remote.getName().equalsIgnoreCase(
				        candidateProxyManager[i].getName()))
				{
					if (i < candidateProxyManager.length - 1)
					{
						processHttpRequest(candidateProxyManager[i + 1],
						        proxyRequest);
						return;
					}
				}
			}
		}
		doClose();
	}
}
