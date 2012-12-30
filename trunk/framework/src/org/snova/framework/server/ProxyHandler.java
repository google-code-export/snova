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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
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
	protected Logger	       logger	      = LoggerFactory
	                                                  .getLogger(getClass());
	
	private RemoteProxyHandler	remoteHandler	= null;
	private Channel	           localChannel	  = null;
	
	public Channel getLocalChannel()
	{
		return localChannel;
	}
	
	private Integer	             id;
	
	private static AtomicInteger	seed	= new AtomicInteger(1);
	
	public ProxyHandler()
	{
		id = seed.getAndIncrement();
	}
	
	private void handleChunks(Object e)
	{
		System.out.println("###########" + e.getClass());
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
		doClose();
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
		if (null != localChannel && localChannel.isActive())
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
		if (null != localChannel && localChannel.isActive())
		{
			localChannel.write(chunk);
		}
		else
		{
			doClose();
		}
	}
	
	@Override
	public void handleRawData(RemoteProxyHandler remote, ByteBuf raw)
	{
		
		if (null != localChannel && localChannel.isActive())
		{
			ByteBuf out = localChannel.outboundByteBuffer();
			out.discardReadBytes();
			out.writeBytes(raw);
			raw.clear();
			localChannel.flush();
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
			localChannel.pipeline().remove(ProxyHandler.class);
			localChannel.pipeline().addLast("raw", new RawProxyDataHandler());
		}
	}
	
	public class RawProxyDataHandler extends ChannelInboundByteHandlerAdapter
	{
		@Override
		public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in)
		        throws Exception
		{
			logger.info(String.format("Session[%d] recv local chunk", id));
			handleChunks(in);
			in.clear();
		}
		
		public void channelInactive(ChannelHandlerContext ctx) throws Exception
		{
			doClose();
		}
	}
	
}
