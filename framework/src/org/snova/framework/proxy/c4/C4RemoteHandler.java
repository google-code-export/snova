/**
 * 
 */
package org.snova.framework.proxy.c4;

import java.util.List;
import java.util.Map;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.StringHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
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
import org.snova.framework.common.http.SetCookieHeaderValue;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.SocketConnectionEvent;
import org.snova.framework.event.TCPChunkEvent;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.c4.http.HttpTunnelService;
import org.snova.framework.proxy.c4.ws.WSTunnelService;
import org.snova.framework.proxy.gae.GAEConfig;
import org.snova.framework.proxy.range.MultiRangeFetchTask;
import org.snova.framework.proxy.range.RangeCallback;
import org.snova.framework.server.ProxyHandler;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author yinqiwen
 * 
 */
public class C4RemoteHandler implements RemoteProxyHandler, EventHandler,
        RangeCallback
{
	protected static Logger	                     logger	         = LoggerFactory
	                                                                     .getLogger(C4RemoteHandler.class);
	private static Map<Integer, C4RemoteHandler>	sessionTable	= new ConcurrentHashMap<Integer, C4RemoteHandler>();
	
	private C4ServerAuth	                     server;
	private LocalProxyHandler	                 localHandler;
	private int	                                 sequence	     = 0;
	
	private HttpTunnelService	                 http;
	private WSTunnelService	                     ws;
	private SimpleSocketAddress	                 proxyAddr;
	private MultiRangeFetchTask	                 rangeTask	     = null;
	private boolean	                             isClosed;
	boolean	                                     injectRange	 = false;
	
	public static C4RemoteHandler getSession(int sid)
	{
		return sessionTable.get(sid);
	}
	
	public C4RemoteHandler(C4ServerAuth s)
	{
		this.server = s;
		if (isWebsocketServer())
		{
			ws = WSTunnelService.getWSTunnelService(s);
		}
		else
		{
			http = HttpTunnelService.getHttpTunnelService(s);
		}
		
	}
	
	private boolean isWebsocketServer()
	{
		return (server.url.getScheme().equalsIgnoreCase("ws") || server.url
		        .getScheme().equalsIgnoreCase("wss"));
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
		}
		else
		{
			if (req.getMethod().equals(HttpMethod.GET))
			{
				IniProperties cfg = SnovaConfiguration.getInstance()
				        .getIniProperties();
				if (cfg.getIntProperty("C4", "MultiRangeFetchEnable", 0) == 1)
				{
					if (StringHelper.containsString(HttpHeaders.getHost(req),
					        C4Config.injectRange) || injectRange)
					{
						
						rangeTask = new MultiRangeFetchTask();
						rangeTask.sessionID = local.getId();
						rangeTask.fetchLimit = cfg.getIntProperty("GAE",
						        "RangeFetchLimitSize", 256 * 1024);
						rangeTask.fetchWorkerNum = cfg.getIntProperty("GAE",
						        "RangeConcurrentFetcher", 3);
						rangeTask.asyncGet((HTTPRequestEvent) ev, this);
						return;
					}
				}
			}
		}
		requestEvent(ev);
	}
	
	void requestEvent(Event ev)
	{
		if (isWebsocketServer())
		{
			if (null != ws)
			{
				ws.write(ev);
			}
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
			logger.info(String.format("Session[%d]closed", localHandler.getId()));
			SocketConnectionEvent closeEv = new SocketConnectionEvent();
			closeEv.status = SocketConnectionEvent.TCP_CONN_CLOSED;
			closeEv.addr = proxyAddr.toString();
			closeEv.setHash(localHandler.getId());
			requestEvent(closeEv);
			int sid = null != localHandler ? localHandler.getId() : 0;
			sessionTable.remove(sid);
		}
		if (null != rangeTask)
		{
			rangeTask.close();
		}
	}
	
	@Override
	public void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_RESPONSE_EVENT_TYPE:
			{
				logger.info(String.format(
				        "Session[%d]Handle HTTP response event.",
				        localHandler.getId()));
				HTTPResponseEvent res = (HTTPResponseEvent) event;
				if (null != rangeTask)
				{
					if (!rangeTask.processAsyncResponse(res))
					{
						close();
					}
				}
				else
				{
					
				}
				break;
			}
			case CommonEventConstants.EVENT_TCP_CHUNK_TYPE:
			{
				TCPChunkEvent chunk = (TCPChunkEvent) event;
				localHandler.handleRawData(this,
				        ChannelBuffers.wrappedBuffer(chunk.content));
				logger.info(String.format("Session[%d]Handle TCP chunk %d:%d",
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
	
	@Override
	public String getName()
	{
		return "C4";
	}
	
	private HttpResponse buildHttpResponse(HTTPResponseEvent ev)
	{
		int status = ev.statusCode;
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
		        HttpResponseStatus.valueOf(status));
		
		List<KeyValuePair<String, String>> headers = ev.getHeaders();
		for (KeyValuePair<String, String> header : headers)
		{
			response.addHeader(header.getName(), header.getValue());
		}
		
		if (null == response.getHeader(HttpHeaders.Names.CONTENT_LENGTH))
		{
			response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, ""
			        + ev.content.readableBytes());
		}
		
		response.removeHeader(HttpHeaders.Names.TRANSFER_ENCODING);
		if (HttpHeaders.getContentLength(response) == ev.content
		        .readableBytes())
		{
			ChannelBuffer bufer = ChannelBuffers.wrappedBuffer(
			        ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
			response.setContent(bufer);
		}
		else
		{
			response.setChunked(true);
			// response.setTransferEncoding(HttpTransferEncoding.STREAMED);
		}
		return response;
	}
	
	@Override
	public void onHttpResponse(HTTPResponseEvent res)
	{
		if (null != localHandler)
		{
			HttpResponse httpres = buildHttpResponse(res);
			localHandler.handleResponse(this, httpres);
			if (httpres.isChunked())
			{
				Buffer content = res.content;
				HttpChunk chunk = new DefaultHttpChunk(
				        ChannelBuffers.wrappedBuffer(content.getRawBuffer(),
				                content.getReadIndex(), content.readableBytes()));
				localHandler.handleChunk(this, chunk);
			}
		}
		else
		{
			close();
		}
	}
	
	@Override
	public void onRangeChunk(Buffer buf)
	{
		if (null != localHandler)
		{
			HttpChunk chunk = new DefaultHttpChunk(
			        ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
			                buf.getReadIndex(), buf.readableBytes()));
			localHandler.handleChunk(this, chunk);
		}
		else
		{
			close();
		}
	}
	
	@Override
	public void writeHttpReq(HTTPRequestEvent req)
	{
		if (req.url.startsWith("http://"))
		{
			int idx = req.url.indexOf('/', 7);
			if (idx == -1)
			{
				req.url = "/";
			}
			else
			{
				req.url = req.url.substring(idx);
			}
		}
		requestEvent(req);
	}
}
