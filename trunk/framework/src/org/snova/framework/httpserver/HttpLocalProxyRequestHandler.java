/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: XmppAccount.java 
 *
 * @author yinqiwen [ 2010-1-31 | 10:50:02 AM]
 *
 */
package org.snova.framework.httpserver;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;

import org.arch.common.KeyValuePair;
import org.arch.common.Pair;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;

/**
 * @author yinqiwen
 * 
 */
public class HttpLocalProxyRequestHandler extends
        ChannelInboundMessageHandlerAdapter<Object> implements
        LocalProxyHandler
{
	protected Logger logger = LoggerFactory.getLogger(getClass());

	private RemoteProxyHandler remoteHandler = null;
	private Channel localChannel = null;

	private Integer id;

	private static AtomicInteger seed = new AtomicInteger(1);

	public HttpLocalProxyRequestHandler()
	{
		id = seed.getAndIncrement();
	}

	private RemoteProxyHandler getRemoteProxyHandler()
	{
		return null;
	}

	private boolean dispatchEvent(Event event)
	{
		Pair<Channel, Integer> attach = new Pair<Channel, Integer>(
		        localChannel, id);
		event.setAttachment(attach);
		try
		{
			EventDispatcher.getSingletonInstance().dispatch(event);
			return true;
		}
		catch (Exception ex)
		{
			logger.error("Failed to dispatch event.", ex);
			return false;
		}
	}

	private HTTPRequestEvent buildEvent(HttpRequest request)
	{
		HTTPRequestEvent event = new HTTPRequestEvent();
		event.method = request.getMethod().getName();
		event.url = request.getUri();
		event.version = request.getProtocolVersion().getText();
		event.setHash(id);
		event.setAttachment(request);
		ByteBuf content = request.getContent();
		if (null != content)
		{
			content.markReaderIndex();
			int buflen = content.readableBytes();
			event.content.ensureWritableBytes(content.readableBytes());
			content.readBytes(event.content.getRawBuffer(),
			        event.content.getWriteIndex(), content.readableBytes());
			event.content.advanceWriteIndex(buflen);
			content.resetReaderIndex();
		}
		for (String name : request.getHeaderNames())
		{
			for (String value : request.getHeaders(name))
			{
				event.headers
				        .add(new KeyValuePair<String, String>(name, value));
			}

		}
		return event;
	}

	private void handleChunks(Object e)
	{
		if (e instanceof HttpChunk)
		{
			HttpChunk chunk = (HttpChunk) e;
			if (null != remoteHandler)
			{
				remoteHandler.handleChunk(chunk);
			}
		}
		else if (e instanceof ByteBuf)
		{
			if (null != remoteHandler)
			{
				remoteHandler.handleRawData((ByteBuf) e);
			}
		}
		else
		{
			logger.error("Unsupported message type:" + e.getClass());
		}
	}

	private void handleHttpRequest(HttpRequest request)
	{

	}

	public void close()
	{
		Pair<Channel, Integer> attach = new Pair<Channel, Integer>(
		        localChannel, id);
		HTTPConnectionEvent event = new HTTPConnectionEvent(
		        HTTPConnectionEvent.CLOSED);
		event.setAttachment(attach);
		dispatchEvent(event);
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
		}
		close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	        throws Exception
	{
		if (cause instanceof ClosedChannelException)
		{
			if (logger.isDebugEnabled())
			{
				logger.error("Browser connection[" + id + "] exceptionCaught.",
				        cause);
			}
		}
		else
		{
			logger.error("Browser connection[" + id + "] exceptionCaught.",
			        cause);
		}
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
	public void handleResponse(HttpResponse res)
	{
		if (null != localChannel)
		{
			localChannel.write(res);
		}
	}

	@Override
	public void handleChunk(HttpChunk chunk)
	{
		if (null != localChannel)
		{
			localChannel.write(chunk);
		}
	}
}
