/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

import java.net.URL;

import org.arch.buffer.Buffer;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.SocketReadEvent;
import org.snova.framework.proxy.c4.C4ServerAuth;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientCallback;
import org.snova.http.client.HttpClientConnector;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientOptions;
import org.snova.http.client.HttpClientProxyCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpDualConn
{
	private C4ServerAuth server;
	EventHandler cb;
	private int sid;
	private static HttpClient httpClient = null;

	private static void initHttpClient() throws Exception
	{
		if (null != httpClient)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		HttpClientOptions options = new HttpClientOptions();
		options.maxIdleConnsPerHost = cfg.getIntProperty("C4",
		        "ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("C4", "Proxy");
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
					String remoteHost = HostsService.getMappingHost(host);
					Bootstrap b = new Bootstrap();
					ChannelFuture future = b
					        .group(SharedObjectHelper.getEventLoop())
					        .channel(NioSocketChannel.class)
					        .remoteAddress(remoteHost, port)
					        .handler(new HttpClientCodec()).connect();
					return future;
				}
			};
		}
		httpClient = new HttpClient(options, SharedObjectHelper.getEventLoop());
	}

	public HttpDualConn(C4ServerAuth server, EventHandler cb)
	{
		this.server = server;
		this.cb = cb;
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

	HttpReadHandlerCallback read;
	HttpWriteHandlerCallback write;
	boolean running;

	private void writeEvent(Event[] evs, String path,
	        HttpClientCallback callback)
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, server.url.getPath() + path);
		request.setHeader(HttpHeaders.Names.HOST, server.url.getHost());
		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		request.setHeader(C4Constants.USER_TOKEN_HEADER,
		        ConnectionHelper.getUserToken());
		request.setHeader(
		        HttpHeaders.Names.USER_AGENT,
		        cfg.getProperty("C4", "UserAgent",
		                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:15.0) Gecko/20100101 Firefox/15.0.1"));
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");

		Buffer buf = new Buffer(1024);
		for (Event ev : evs)
		{
			ev.encode(buf);
		}
		request.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
		        "" + buf.readableBytes());
		request.setContent(Unpooled.wrappedBuffer(buf.getRawBuffer(),
		        buf.getReadIndex(), buf.readableBytes()));
		try
		{
			httpClient.doRequest(request, callback);
		}
		catch (HttpClientException e)
		{
			e.printStackTrace();
		}
	}

	private Event wrapEvent(Event ev)
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		EncryptType type = EncryptType.valueOf(cfg.getProperty("C4",
		        "Encrypter", "SE1"));
		EncryptEventV2 enc = new EncryptEventV2(type, ev);
		enc.setHash(ev.getHash());
		return enc;
	}

	public void requestEvent(Event ev)
	{
		sid = ev.getHash();
		if (null == read)
		{
			read = new HttpReadHandlerCallback(this);
			SocketReadEvent readEv = new SocketReadEvent();
			readEv.setHash(sid);
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			readEv.maxread = cfg.getIntProperty("C4", "MaxReadBytes", 65536);
			readEv.timeout = cfg.getIntProperty("C4", "ReadTimeout", 25);
			writeEvent(new Event[] { wrapEvent(ev), readEv }, "/pull", read);
		}
		else
		{
			// May be not working if uploading big data
			write = new HttpWriteHandlerCallback();
			writeEvent(new Event[] { wrapEvent(ev) }, "/push", write);
		}
	}
	
	public void close()
	{
		
	}
}
