/**
 * 
 */
package org.snova.framework.proxy.gae;

import java.net.InetSocketAddress;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPErrorEvent;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptType;
import org.arch.util.StringHelper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.common.http.ContentRangeHeaderValue;
import org.snova.framework.common.http.RangeHeaderValue;
import org.snova.framework.common.http.SetCookieHeaderValue;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.EventHeaderTags;
import org.snova.framework.event.EventHelper;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.proxy.common.RangeChunk;
import org.snova.framework.proxy.common.RangeFetchStatus;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.proxy.range.MultiRangeFetchTask;
import org.snova.framework.proxy.range.RangeCallback;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.framework.util.SslCertificateHelper;
import org.snova.http.client.Connector;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientHandler;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * @author wqy
 * 
 */
public class GAERemoteHandler implements RemoteProxyHandler, EventHandler,
        RangeCallback
{
	protected static Logger logger = LoggerFactory
	        .getLogger(GAERemoteHandler.class);
	private static HttpClient client;

	private boolean isHttps;
	private GAEServerAuth gaeAuth;
	private LocalProxyHandler local;
	private HTTPRequestEvent proxyRequest;
	private Set<HttpClientHandler> workingHttpClientHandlers = Collections
	        .synchronizedSet(new HashSet<HttpClientHandler>());
	private MultiRangeFetchTask rangeTask = null;
	private boolean closed = false;
	boolean injectRange = false;

	public GAERemoteHandler(GAEServerAuth auth)
	{
		try
		{
			this.gaeAuth = auth;
			initHttpClient();
		}
		catch (Exception e)
		{
			logger.error("Failed to init http client.", e);
		}
	}

	private static void initHttpClient() throws Exception
	{
		if (null != client)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		Options options = new Options();
		options.maxIdleConnsPerHost = cfg.getIntProperty("GAE",
		        "ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("GAE", "Proxy");
		if (null != proxy)
		{
			final URL proxyUrl = new URL(proxy);
			options.proxyCB = new ProxyCallback()
			{
				@Override
				public URL getProxy(HttpRequest request)
				{
					return proxyUrl;
				}
			};
			options.connector = new Connector()
			{
				@Override
				public ChannelFuture connect(String host, int port)
				{
					String remoteHost = HostsService.getMappingHost(host);
					ChannelFuture future = SharedObjectHelper
					        .getClientBootstrap().connect(
					                new InetSocketAddress(remoteHost, port));
					if (proxyUrl.getProtocol().equalsIgnoreCase("https"))
					{
						SSLContext sslContext = null;
						try
						{
							sslContext = SSLContext.getDefault();
						}
						catch (NoSuchAlgorithmException e)
						{
							logger.error("", e);
						}

						SSLEngine sslEngine = sslContext.createSSLEngine();
						sslEngine.setUseClientMode(true);
						final SslHandler ssl = new SslHandler(sslEngine);
						future.getChannel().getPipeline().addLast("ssl", ssl);
					}
					return future;
				}
			};
		}
		client = new HttpClient(options,
		        SharedObjectHelper.getClientBootstrap());
	}

	private HttpResponse buildHttpResponse(HTTPResponseEvent ev)
	{
		int status = ev.statusCode;
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1,
		        HttpResponseStatus.valueOf(status));

		List<KeyValuePair<String, String>> headers = ev.getHeaders();
		for (KeyValuePair<String, String> header : headers)
		{
			if (header.getName().equalsIgnoreCase(HttpHeaders.Names.SET_COOKIE)
			        || header.getName().equalsIgnoreCase(
			                HttpHeaders.Names.SET_COOKIE2))
			{
				List<SetCookieHeaderValue> cookies = SetCookieHeaderValue
				        .parse(header.getValue());
				for (SetCookieHeaderValue cookie : cookies)
				{
					response.addHeader(header.getName(), cookie.toString());
				}
			}
			else
			{
				response.addHeader(header.getName(), header.getValue());
			}
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

	private HTTPRequestEvent buildHttpRequestEvent(HttpRequest request)
	{
		HTTPRequestEvent event = new HTTPRequestEvent();
		event.method = request.getMethod().getName();
		event.url = request.getUri();
		if (!event.url.startsWith("http://")
		        && !event.url.startsWith("https://"))
		{
			if (isHttps)
			{
				event.url = "https://" + HttpHeaders.getHost(request)
				        + event.url;
			}
			else
			{
				event.url = "http://" + HttpHeaders.getHost(request)
				        + event.url;
			}
		}
		event.version = request.getProtocolVersion().getText();
		event.setHash(local.getId());
		event.setAttachment(request);
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

	private void tryProxyRequest()
	{
		if (null == proxyRequest)
		{
			return;
		}
		if (proxyRequest.getContentLength() <= proxyRequest.content
		        .readableBytes())
		{
			requestEvent(proxyRequest, this);
			proxyRequest.content.setReadIndex(0);
		}
	}

	private boolean fillHttpRequestBody(ChannelBuffer buf)
	{
		if (null != proxyRequest)
		{
			return false;
		}
		int len = buf.readableBytes();
		proxyRequest.content.ensureWritableBytes(len);
		buf.readBytes(proxyRequest.content.getRawBuffer(),
		        proxyRequest.content.getWriteIndex(), len);
		proxyRequest.content.advanceWriteIndex(len);
		return true;
	}

	private SSLContext prepareSslContext(HttpRequest req)
	{
		String httpshost = HttpHeaders.getHost(req);
		SimpleSocketAddress addr = HttpClientHelper.getHttpRemoteAddress(true,
		        httpshost);
		SSLContext sslContext = null;
		try
		{
			sslContext = SslCertificateHelper.getFakeSSLContext(addr.host, ""
			        + addr.port);
		}
		catch (Exception e)
		{
			logger.error("Failed to init sslcontext", e);
		}

		return sslContext;
	}

	@Override
	public void handleRequest(final LocalProxyHandler local,
	        final HttpRequest req)
	{
		this.local = local;
		if (req.getMethod().equals(HttpMethod.CONNECT))
		{
			isHttps = true;
			HttpResponse establised = new DefaultHttpResponse(
			        req.getProtocolVersion(), HttpResponseStatus.OK);
			final SSLContext sslCtx = prepareSslContext(req);
			if (null == sslCtx)
			{
				HttpResponse fail = new DefaultHttpResponse(
				        req.getProtocolVersion(),
				        HttpResponseStatus.SERVICE_UNAVAILABLE);
				local.handleResponse(this, fail);
				return;
			}
			if (null == local.getLocalChannel())
			{
				close();
				return;
			}
			local.getLocalChannel().write(establised)
			        .addListener(new ChannelFutureListener()
			        {
				        public void operationComplete(ChannelFuture future)
				                throws Exception
				        {
					        if (future.isDone())
					        {
						        SocketChannel ch = (SocketChannel) local
						                .getLocalChannel();
						        if (ch.getPipeline().get(SslHandler.class) == null)
						        {
							        InetSocketAddress remote = ch
							                .getRemoteAddress();
							        SSLEngine engine = sslCtx.createSSLEngine(
							                remote.getAddress()
							                        .getHostAddress(), remote
							                        .getPort());
							        engine.setUseClientMode(false);
							        ch.getPipeline().addBefore("decoder",
							                "ssl", new SslHandler(engine));
						        }
					        }
				        }
			        });
			return;
		}
		rangeTask = null;
		proxyRequest = buildHttpRequestEvent(req);

		if (proxyRequest.method.equalsIgnoreCase("GET"))
		{
			if (StringHelper.containsString(HttpHeaders.getHost(req),
			        GAEConfig.injectRange) || injectRange)
			{
				IniProperties cfg = SnovaConfiguration.getInstance()
				        .getIniProperties();
				rangeTask = new MultiRangeFetchTask();
				rangeTask.sessionID = local.getId();
				rangeTask.fetchLimit = cfg.getIntProperty("GAE",
				        "RangeFetchLimitSize", 256 * 1024);
				rangeTask.fetchWorkerNum = cfg.getIntProperty("GAE",
				        "RangeConcurrentFetcher", 3);
				rangeTask.asyncGet(proxyRequest, this);
				return;
			}

		}
		tryProxyRequest();
	}

	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
		fillHttpRequestBody(chunk.getContent());
		tryProxyRequest();
	}

	@Override
	public void handleRawData(LocalProxyHandler local, ChannelBuffer raw)
	{
		logger.error("Unsupported raw data in GAE.");
	}

	@Override
	public void close()
	{
		synchronized (workingHttpClientHandlers)
		{
			for (HttpClientHandler ch : workingHttpClientHandlers)
			{
				ch.closeChannel();
			}
			workingHttpClientHandlers.clear();
		}
		if (null != rangeTask)
		{
			rangeTask.close();
		}
		closed = true;
	}

	private void processProxyResponse(HTTPResponseEvent response)
	{
		if (null != rangeTask)
		{
			if (!rangeTask.processAsyncResponse(response))
			{
				close();
			}
			return;
		}
		else
		{
			String originRange = proxyRequest.getHeader("Range");
			String contentRange = response.getHeader("Content-Range");
			if (response.statusCode == 206
			        && !StringHelper.isEmptyString(contentRange)
			        && proxyRequest.method.equalsIgnoreCase("GET"))
			{
				ContentRangeHeaderValue cv = new ContentRangeHeaderValue(
				        contentRange);
				long length = cv.getInstanceLength();
				if (!StringHelper.isEmptyString(originRange))
				{
					RangeHeaderValue rv = new RangeHeaderValue(originRange);
					if (rv.getLastBytePos() > 0)
					{
						length = rv.getLastBytePos() + 1;
					}
				}
				if (length > cv.getLastBytePos() + 1)
				{
					IniProperties cfg = SnovaConfiguration.getInstance()
					        .getIniProperties();
					rangeTask = new MultiRangeFetchTask();
					rangeTask.sessionID = local.getId();
					rangeTask.fetchLimit = cfg.getIntProperty("GAE",
					        "RangeFetchLimitSize", 256 * 1024);
					rangeTask.fetchWorkerNum = cfg.getIntProperty("GAE",
					        "RangeConcurrentFetcher", 3);
					rangeTask.setRangeCallback(this);
					rangeTask
					        .setRangeState(MultiRangeFetchTask.STATE_WAIT_HEAD_RES);
					rangeTask.processAsyncResponse(response);
					return;
				}
				if (StringHelper.isEmptyString(originRange))
				{
					response.statusCode = 200;
					response.removeHeader("Content-Range");
				}
			}
			writeLocalHttpResponse(response);
		}
	}

	@Override
	public void onEvent(EventHeader header, Event event)
	{
		switch (header.type)
		{
			case HTTPEventContants.HTTP_RESPONSE_EVENT_TYPE:
			{
				HTTPResponseEvent response = (HTTPResponseEvent) event;
				processProxyResponse(response);
				break;
			}
			case HTTPEventContants.HTTP_ERROR_EVENT_TYPE:
			{
				HTTPErrorEvent error = (HTTPErrorEvent) event;
				logger.error("Receive error:" + error.errno + ":" + error.error);
				break;
			}
			default:
			{
				logger.error("Unsupported event type:" + event.getClass());
				break;
			}
		}
	}

	private Event wrapEvent(Event ev)
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		EncryptType encType = EncryptType.valueOf(cfg.getProperty("GAE",
		        "Encrypter", "SE1").toUpperCase());
		EncryptEvent enc = new EncryptEvent(encType, ev);
		return enc;
	}

	public void requestEvent(Event ev, EventHandler handler)
	{
		if (null == handler)
		{
			handler = this;
		}
		requestEvent(ev, handler, new GAEFutureCallback(handler, ev));
	}

	private void requestEvent(Event ev, EventHandler handler,
	        GAEFutureCallback cb)
	{
		try
		{
			GAEServerAuth auth = gaeAuth;
			if (null == auth)
			{
				auth = GAE.servers.select();
			}
			int sid = null == local ? 0 : local.getId();
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
			        HttpMethod.POST, "/invoke");
			request.setHeader(HttpHeaders.Names.HOST, auth.appid
			        + ".appspot.com");
			request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
			request.setHeader(
			        HttpHeaders.Names.USER_AGENT,
			        cfg.getProperty("GAE", "UserAgent",
			                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:15.0) Gecko/20100101 Firefox/15.0.1"));
			request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
			        "application/octet-stream");

			EventHeaderTags tags = new EventHeaderTags();
			tags.token = auth.token;
			ev.setHash(sid);
			ev = wrapEvent(ev);
			Buffer buf = EventHelper.encodeEvent(tags, ev);
			request.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
			        "" + buf.readableBytes());
			request.setContent(ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
			        buf.getReadIndex(), buf.readableBytes()));
			HttpClientHandler h = client.execute(request, cb);
			workingHttpClientHandlers.add(h);
			cb.httpHandler = h;
		}
		catch (HttpClientException e)
		{
			logger.error("Failed to proxy request.", e);
		}
	}

	class GAEFutureCallback implements FutureCallback
	{
		private int failedCount;
		private Buffer bodyContent = new Buffer(1024);
		private long bodyLength = 0;
		private HTTPRequestEvent backupEvent = null;
		private HttpClientHandler httpHandler = null;

		public GAEFutureCallback(EventHandler handler, Event ev)
		{
			ev = Event.extractEvent(ev);
			if (ev instanceof HTTPRequestEvent)
			{
				backupEvent = (HTTPRequestEvent) ev;
			}
			this.handler = handler;
		}

		void clear()
		{
			bodyLength = 0;
			bodyContent.clear();
			failedCount = 0;
		}

		EventHandler handler;

		private void removeHttpClientHandler()
		{
			if (null != httpHandler)
			{
				workingHttpClientHandlers.remove(httpHandler);
				httpHandler = null;
			}
		}

		private void onError()
		{
			removeHttpClientHandler();
			if (closed)
			{
				return;
			}
			failedCount++;
			if (failedCount < 2 && null != backupEvent && !closed)
			{
				backupEvent.content.setReadIndex(0);
				requestEvent(backupEvent, GAERemoteHandler.this, this);
				bodyContent.clear();
			}
			else
			{
				if (null != local)
				{
					local.close();
				}
			}
		}

		private void fillBodyContent(ChannelBuffer buf)
		{
			int len = buf.readableBytes();
			bodyContent.ensureWritableBytes(len);
			buf.readBytes(bodyContent.getRawBuffer(),
			        bodyContent.getWriteIndex(), len);
			bodyContent.advanceWriteIndex(len);
			if (bodyContent.readableBytes() >= bodyLength)
			{
				try
				{
					Event ev = EventHelper.parseEvent(bodyContent);
					ev = Event.extractEvent(ev);
					EventHeader header = new EventHeader(
					        Event.getTypeVersion(ev.getClass()), ev.getHash());
					ev.setAttachment(this);
					handler.onEvent(header, ev);
					removeHttpClientHandler();
				}
				catch (Exception e)
				{
					logger.error("", e);
				}
			}
		}

		@Override
		public void onResponse(HttpResponse res)
		{
			if (res.getStatus().getCode() != 200)
			{
				logger.error("Unexpected response:" + res);
				onError();
			}
			else
			{
				bodyLength = HttpHeaders.getContentLength(res);
				bodyContent.clear();
				fillBodyContent(res.getContent());
			}
		}

		@Override
		public void onBody(HttpChunk chunk)
		{
			fillBodyContent(chunk.getContent());
		}

		@Override
		public void onError(String error)
		{
			logger.warn("Recv error:" + error + " for " + hashCode());
			onError();
		}

		@Override
		public void onComplete(HttpResponse res)
		{
		}
	}

	@Override
	public String getName()
	{
		return "GAE";
	}

	private void writeLocalHttpResponse(HTTPResponseEvent res)
	{
		HttpResponse httpres = buildHttpResponse(res);
		if (!httpres.containsHeader("Connection"))
		{
			httpres.setHeader("Connection", "close");
		}
		local.handleResponse(this, httpres);
		if (httpres.isChunked())
		{
			Buffer content = res.content;
			HttpChunk chunk = new DefaultHttpChunk(
			        ChannelBuffers.wrappedBuffer(content.getRawBuffer(),
			                content.getReadIndex(), content.readableBytes()));
			local.handleChunk(this, chunk);
		}
	}

	@Override
	public void onHttpResponse(HTTPResponseEvent res)
	{
		writeLocalHttpResponse(res);
	}

	@Override
	public void onRangeChunk(Buffer buf)
	{
		if (null != local)
		{
			HttpChunk chunk = new DefaultHttpChunk(
			        ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
			                buf.getReadIndex(), buf.readableBytes()));
			local.handleChunk(this, chunk);
		}
		else
		{
			if (null != rangeTask)
			{
				rangeTask.close();
			}
		}

	}

	@Override
	public void writeHttpReq(HTTPRequestEvent req)
	{
		requestEvent(req, this);
	}
}
