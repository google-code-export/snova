/**
 * 
 */
package org.snova.framework.proxy.c4;

import java.util.Map;

import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.ArraysHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.SocketConnectionEvent;
import org.snova.framework.event.TCPChunkEvent;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.c4.http.HttpTunnelService;
import org.snova.framework.server.ProxyHandler;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author yinqiwen
 * 
 */
public class C4RemoteHandler implements RemoteProxyHandler, EventHandler
{
	protected static Logger logger = LoggerFactory
	        .getLogger(C4RemoteHandler.class);
	private static Map<Integer, C4RemoteHandler> sessionTable = new ConcurrentHashMap<Integer, C4RemoteHandler>();

	private C4ServerAuth server;
	private LocalProxyHandler localHandler;
	private int sequence = 0;

	private HttpTunnelService http;
	private SimpleSocketAddress proxyAddr;
	private boolean isHttps;
	private boolean isClosed;
	private CheckHttpStatus checkStatus;

	class CheckHttpStatus
	{
		int length = -1;
		int restBody = -1;
		boolean chunked;
	}

	public static C4RemoteHandler getSession(int sid)
	{
		return sessionTable.get(sid);
	}

	public C4RemoteHandler(C4ServerAuth s)
	{
		this.server = s;
		http = HttpTunnelService.getHttpTunnelService(s);
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
		if (event.url.startsWith("http://"))
		{
			int idx = event.url.indexOf('/', 7);
			if (idx == -1)
			{
				event.url = "/";
			}
			else
			{
				event.url = event.url.substring(idx);
			}
		}
		if(request.getHeader("Content-Length") == null)
		{
			System.out.println("#########@@@@" + request.getProtocolVersion());
		}
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
		ChannelBuffer content = request.getContent();
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
		sessionTable.put(local.getId(), this);
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
			ChannelPipeline pipeline = local.getLocalChannel().getPipeline();
			ProxyHandler p = (ProxyHandler) pipeline.get("handler");
			p.switchRawHandler();
			isHttps = true;
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
			if (null != http)
			{
				http.write(ev);
			}
		}
	}

	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
		handleRawData(local, chunk.getContent());
	}

	@Override
	public void handleRawData(LocalProxyHandler local, ChannelBuffer raw)
	{
		TCPChunkEvent ev = new TCPChunkEvent();
		ev.setHash(local.getId());
		ev.sequence = this.sequence++;
		ChannelBuffer buf = raw;
		ev.content = new byte[buf.readableBytes()];
		buf.readBytes(ev.content);
		requestEvent(ev);
	}

	@Override
	public void close()
	{
		if (!isClosed)
		{
			logger.info(String.format("Session[%d]closed", localHandler.getId()), new Exception());
			SocketConnectionEvent closeEv = new SocketConnectionEvent();
			closeEv.status = SocketConnectionEvent.TCP_CONN_CLOSED;
			closeEv.addr = proxyAddr.toString();
			closeEv.setHash(localHandler.getId());
			http.write(closeEv);
			int sid = null != localHandler ? localHandler.getId() : 0;
			sessionTable.remove(sid);
		}
	}

//	private void checkHttpBody(TCPChunkEvent chunk)
//	{
//		if (isHttps)
//		{
//			return;
//		}
//		boolean isHeader = false;
//		if (chunk.content.length >= 10)
//		{
//			String tmp = new String(chunk.content, 0, 9);
//			if (tmp.equalsIgnoreCase("HTTP/1.1 "))
//			{
//				isHeader = true;
//			}
//		}
//		if (isHeader)
//		{
//			checkStatus = new CheckHttpStatus();
//			int index = ArraysHelper.indexOf(chunk.content,
//			        "\r\n\r\n".getBytes());
//			if (index <= 0)
//			{
//				logger.error("####Failed to found end CRLF");
//				return;
//			}
//			String res = new String(chunk.content, 0, index);
//			String[] headers = res.split("\r\n");
//			for (String h : headers)
//			{
//				String[] nv = h.split(":");
//				if (nv.length > 1)
//				{
//					if (nv[0].trim().equalsIgnoreCase("Content-Length"))
//					{
//						checkStatus.length = Integer.parseInt(nv[1].trim());
//						checkStatus.restBody = checkStatus.length;
//					}
//					if (nv[0].trim().equalsIgnoreCase("Transfer-Encoding"))
//					{
//						checkStatus.chunked = nv[1].contains("chunked");
//					}
//				}
//			}
//			if (checkStatus.restBody > 0)
//			{
//				checkStatus.restBody -= (chunk.content.length - index - 4);
//			}
//		}
//		else
//		{
//			if (checkStatus.length > 0)
//			{
//				checkStatus.restBody -= chunk.content.length;
//			}
//			else if (checkStatus.chunked)
//			{
//				if (ArraysHelper.indexOf(chunk.content, "0\r\n\r\n".getBytes()) != -1)
//				{
//					checkStatus.restBody = 0;
//				}
//			}
//		}
//
//	}

	@Override
	public void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case CommonEventConstants.EVENT_TCP_CHUNK_TYPE:
			{
				TCPChunkEvent chunk = (TCPChunkEvent) event;
				// checkHttpBody(chunk);
				localHandler.handleRawData(this,
				        ChannelBuffers.wrappedBuffer(chunk.content));
				logger.info(String.format("Session[%d]Handle chunk %d:%d",
				        localHandler.getId(), chunk.sequence,
				        chunk.content.length));
//				if(chunk.content.length == 68)
//				{
//					System.out.println(proxyAddr + "#######" + new String(chunk.content) );
//				}
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

	@Override
	public String getName()
	{
		return "C4";
	}
}
