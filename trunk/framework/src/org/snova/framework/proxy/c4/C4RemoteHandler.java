/**
 * 
 */
package org.snova.framework.proxy.c4;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.SocketConnectionEvent;
import org.snova.framework.event.TCPChunkEvent;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.c4.http.HttpDualConn;
import org.snova.framework.server.ProxyHandler;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author qiyingwang
 * 
 */
public class C4RemoteHandler implements RemoteProxyHandler, EventHandler
{
	protected static Logger	    logger	 = LoggerFactory
	                                             .getLogger(C4RemoteHandler.class);
	private C4ServerAuth	    server;
	private LocalProxyHandler	localHandler;
	private int	                sequence	= 0;
	
	private HttpDualConn	    http;
	private SimpleSocketAddress	proxyAddr;
	
	public C4RemoteHandler(C4ServerAuth s)
	{
		this.server = s;
		http = new HttpDualConn(s, this);
	}
	
	private boolean isWebsocketServer()
	{
		return (server.url.getProtocol().equalsIgnoreCase("ws") || server.url
		        .getProtocol().equalsIgnoreCase("wss"));
	}
	
	private HTTPRequestEvent buildHttpRequestEvent(HttpRequest request)
	{
		HTTPRequestEvent event = new HTTPRequestEvent();
		event.method = request.getMethod().getName();
		event.url = request.getUri();
		event.version = request.getProtocolVersion().getText();
		event.setHash(localHandler.getId());
		event.setAttachment(request);
		String host = HttpHeaders.getHost(request);
		if (null == host)
		{
			logger.error(String.format("Session[%d] invalid request.",
			        localHandler.getId(), request));
			return null;
		}
		proxyAddr = HttpClientHelper.getHttpRemoteAddress(request.getMethod()
		        .equals(HttpMethod.CONNECT), HttpHeaders.getHost(request));
		ByteBuf content = request.getContent();
		if (null != content)
		{
			content.markReaderIndex();
			int buflen = content.readableBytes();
			event.content.ensureWritableBytes(content.readableBytes());
			content.readBytes(event.content.getRawBuffer(),
			        event.content.getWriteIndex(), content.readableBytes());
			event.content.advanceWriteIndex(buflen);
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
	
	@Override
	public void handleRequest(LocalProxyHandler local, HttpRequest req)
	{
		localHandler = local;
		logger.info(String.format("Session[%d]Request %s %s",
		        localHandler.getId(), req.getMethod(), req.getUri()));
		Event ev = buildHttpRequestEvent(req);
		if (null == ev)
		{
			local.close();
			return;
		}
		if (req.getMethod().equals(HttpMethod.CONNECT))
		{
			ChannelPipeline pipeline = local.getLocalChannel().pipeline();
			pipeline.remove(HttpRequestDecoder.class);
			pipeline.remove(HttpResponseEncoder.class);
			ProxyHandler p = (ProxyHandler) pipeline
			        .get(ChannelInboundMessageHandlerAdapter.class);
			p.switchRawHandler();
		}
		requestEvent(ev);
	}
	
	void requestEvent(Event ev)
	{
		if (isWebsocketServer())
		{
			
		}
		else
		{
			
			http.requestEvent(ev);
			// http.requestEvent(buildHttpRequestEvent(req));
		}
	}
	
	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
		handleRawData(local, chunk.getContent());
	}
	
	@Override
	public void handleRawData(LocalProxyHandler local, ByteBuf raw)
	{
		TCPChunkEvent ev = new TCPChunkEvent();
		ev.setHash(local.getId());
		ev.sequence = this.sequence++;
		ByteBuf buf = raw;
		ev.content = new byte[buf.readableBytes()];
		buf.readBytes(ev.content);
		requestEvent(ev);
	}
	
	@Override
	public void close()
	{
		logger.info(String.format("Session[%d]closed", localHandler.getId()));
		if (null != http)
		{
			SocketConnectionEvent closeEv = new SocketConnectionEvent();
			closeEv.status = SocketConnectionEvent.TCP_CONN_CLOSED;
			closeEv.addr = proxyAddr.toString();
			closeEv.setHash(localHandler.getId());
			http.requestEvent(closeEv);
			http.close();
			http = null;
		}
		
	}
	
	@Override
	public void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case CommonEventConstants.EVENT_TCP_CHUNK_TYPE:
			{
				TCPChunkEvent chunk = (TCPChunkEvent) event;
				localHandler.handleRawData(this,
				        Unpooled.wrappedBuffer(chunk.content));
				logger.info(String.format("Session[%d]Handle chunk %d:%d",
				        localHandler.getId(), chunk.sequence,
				        chunk.content.length));
				break;
			}
			case CommonEventConstants.EVENT_TCP_CONNECTION_TYPE:
			{
				SocketConnectionEvent ev = (SocketConnectionEvent) event;
				if (ev.status == SocketConnectionEvent.TCP_CONN_CLOSED)
				{
					if (null != proxyAddr
					        && proxyAddr.toString().equals(ev.addr))
					{
						close();
						localHandler.close();
					}
				}
				break;
			}
		}
	}
}
