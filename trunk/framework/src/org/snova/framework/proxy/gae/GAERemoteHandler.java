/**
 * 
 */
package org.snova.framework.proxy.gae;

import java.net.URL;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.EventHeaderTags;
import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientCallback;
import org.snova.http.client.HttpClientConnector;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientOptions;
import org.snova.http.client.HttpClientProxyCallback;

/**
 * @author wqy
 * 
 */
public class GAERemoteHandler implements RemoteProxyHandler
{
	protected static Logger	  logger	= LoggerFactory
	                                         .getLogger(GAERemoteHandler.class);
	private GAEServerAuth	  auth;
	private HttpClient	      client;
	private LocalProxyHandler	local;
	
	public GAERemoteHandler()
	{
		try
		{
			initHttpClient();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void initHttpClient() throws Exception
	{
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		HttpClientOptions options = new HttpClientOptions();
		options.maxIdleConnsPerHost = cfg.getIntProperty("GAE",
		        "ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("GAE", "Proxy");
		if (null != proxy)
		{
			final URL proxyUrl = new URL(proxy);
			options.proxyCB = new HttpClientProxyCallback()
			{
				@Override
				public URL getProxy(HttpRequest request)
				{
					return proxyUrl;
				}
			};
			options.connector = new HttpClientConnector()
			{
				@Override
				public ChannelFuture connect(String host, int port)
				{
					return null;
				}
			};
		}
		client = new HttpClient(options, SharedObjectHelper.getEventLoop());
	}
	
	private HTTPRequestEvent buildEvent(HttpRequest request)
	{
		HTTPRequestEvent event = new HTTPRequestEvent();
		event.method = request.getMethod().getName();
		event.url = request.getUri();
		event.version = request.getProtocolVersion().getText();
		event.setHash(local.getId());
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
	
	@Override
	public void handleRequest(LocalProxyHandler local, final HttpRequest req)
	{
		
	}
	
	@Override
	public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
	{
		
	}
	
	@Override
	public void handleRawData(LocalProxyHandler local, ByteBuf raw)
	{
		logger.error("Unsupported raw data in GAE.");
	}
	
	@Override
	public void close()
	{
	}
	
	void requestEvent(Event ev)
	{
		try
		{
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
			
			Buffer buf = new Buffer(1024);
			EventHeaderTags tags = new EventHeaderTags();
			tags.token = "";
			ev.setHash(local.getId());
			CompressorType compressType = CompressorType.valueOf(cfg
			        .getProperty("GAE", "Compressor", "Snappy").toUpperCase());
			CompressEvent comress = new CompressEvent(compressType, ev);
			comress.setHash(local.getId());
			EncryptType encType = EncryptType.valueOf(cfg.getProperty("GAE",
			        "Encrypter", "SE1").toUpperCase());
			EncryptEvent enc = new EncryptEvent(encType, comress);
			enc.setHash(local.getId());
			enc.encode(buf);
			request.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
			        "" + buf.readableBytes());
			request.setContent(Unpooled.wrappedBuffer(buf.getRawBuffer(),
			        buf.getReadIndex(), buf.readableBytes()));
			client.doRequest(request, new GAEHttpClientCallback());
		}
		catch (HttpClientException e)
		{
			logger.error("Failed to proxy request.", e);
		}
	}
	
	class GAEHttpClientCallback implements HttpClientCallback
	{
		
		@Override
		public void onResponse(HttpResponse res)
		{
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public void onBody(HttpChunk chunk)
		{
			
		}
		
		@Override
		public void onError(String error)
		{
			// TODO Auto-generated method stub
			
		}
		
	}
}
