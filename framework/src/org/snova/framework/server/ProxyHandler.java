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

import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.RemoteProxyManager;
import org.snova.framework.proxy.spac.SPAC;
import org.snova.framework.util.MiscHelper;

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
	private Map<String, String>	 proxyAttr	              = new HashMap<String, String>();
	private RemoteProxyHandler	 remoteHandler	          = null;
	private Channel	             localChannel	          = null;
	private boolean	             isHttps;
	
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
	
	public boolean isHttps()
	{
		return isHttps;
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
		logger.info(String.format("Session[%d]Select [%s] for %s", id,
		        rm.getName(), MiscHelper.getURLString(request, true)));
		remoteHandler = rm.createProxyHandler(proxyAttr);
		remoteHandler.handleRequest(this, request);
	}
	
	private void handleHttpRequest(HttpRequest request)
	{
		if (request.getMethod().equals(HttpMethod.CONNECT))
		{
			isHttps = true;
		}
		request.removeHeader("Proxy-Connection");
		candidateProxyManager = SPAC.selectProxy(request, serverType, proxyAttr,
		        this);
		if (null == candidateProxyManager)
		{
			return;
		}
		
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
		if (e.getCause() instanceof ClosedChannelException)
		{
			return;
		}
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
			if (localChannel.getPipeline().get("encoder") != null)
			{
				localChannel.getPipeline().remove("encoder");
			}
			if (localChannel.getPipeline().get("decoder") != null)
			{
				localChannel.getPipeline().remove("decoder");
			}
		}
	}
	
	@Override
	public void onProxyFailed(RemoteProxyHandler remote,
	        HttpRequest proxyRequest)
	{
		logger.warn("Proxy:" + remote + " failed for "
		        + HttpHeaders.getHost(proxyRequest));
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
