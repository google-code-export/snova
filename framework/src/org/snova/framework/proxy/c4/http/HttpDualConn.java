/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.net.InetSocketAddress;
import java.net.URL;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.event.EventHelper;
import org.snova.framework.event.SocketReadEvent;
import org.snova.framework.proxy.c4.C4ServerAuth;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.HttpClient;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.Connector;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientHandler;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpDualConn
{
	protected static Logger logger = LoggerFactory
	        .getLogger(HttpDualConn.class);
	private C4ServerAuth server;
	EventHandler cb;
	int sid;
	private boolean closed = false;
	private static HttpClient httpClient = null;

	private static void initHttpClient() throws Exception
	{
		if (null != httpClient)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		Options options = new Options();
		options.maxIdleConnsPerHost = cfg.getIntProperty("C4",
		        "ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("C4", "Proxy");
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
					return SharedObjectHelper.getClientBootstrap().connect(
					        new InetSocketAddress(remoteHost, port));
				}
			};
		}
		httpClient = new HttpClient(options,
		        SharedObjectHelper.getClientBootstrap());
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

	private HttpClientHandler writeEvent(Event[] evs, String path,
	        FutureCallback callback, KeyValuePair<String, String> header)
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, server.url.toString() + path);
		int port = 80;
		if (server.url.getPort() > 0)
		{
			port = server.url.getPort();
		}
		else
		{
			if (server.url.getProtocol().equalsIgnoreCase("https"))
			{
				port = 443;
			}
		}
		request.setHeader(HttpHeaders.Names.HOST, server.url.getHost() + ":"
		        + port);
		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		request.setHeader("UserToken", NetworkHelper.getMacAddress());
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
		if (null != header)
		{
			request.setHeader(header.getName(), header.getValue());
		}

		request.setContent(ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
		        buf.getReadIndex(), buf.readableBytes()));

		try
		{
			return httpClient.execute(request, callback);
		}
		catch (HttpClientException e)
		{
			e.printStackTrace();
		}
		return null;
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

	void startWriteTask(final Event ev)
	{
		HttpDualConn.this.write = new HttpWriteHandlerCallback(
		        HttpDualConn.this);
		writeEvent(new Event[] { wrapEvent(ev) }, "push", write, null);
		EventHelper.resetEncodedEvent(ev);
		write.cacheEvent = ev;
	}

	void startReadTask()
	{
		if (closed || null != read)
		{
			// logger.info(String.format(
			// "Session[%d]is closed or read task is not null.", sid));
			return;
		}
		read = new HttpReadHandlerCallback(HttpDualConn.this);
		SocketReadEvent readEv = new SocketReadEvent();
		readEv.setHash(sid);
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		readEv.maxread = cfg.getIntProperty("C4", "MaxReadBytes", 65536);
		readEv.timeout = cfg.getIntProperty("C4", "ReadTimeout", 25);
		KeyValuePair<String, String> addition = new KeyValuePair<String, String>(
		        "C4MiscInfo", readEv.timeout + "_" + readEv.maxread);
		HttpClientHandler h = writeEvent(new Event[] { readEv }, "pull", read,
		        addition);
		read.httpClient = h;
		// logger.info(String.format("Session[%d] restart read task:%d", sid,
		// read.hashCode()));

	}

	public void requestEvent(Event ev)
	{
		sid = ev.getHash();
		if (null == read && ev instanceof HTTPRequestEvent)
		{
			if (ev instanceof HTTPRequestEvent)
			{
				HTTPRequestEvent hreq = (HTTPRequestEvent) ev;
				if (hreq.method.equalsIgnoreCase("Connect"))
				{
					startWriteTask(ev);
					startReadTask();
					return;
				}
			}
			read = new HttpReadHandlerCallback(this);
			SocketReadEvent readEv = new SocketReadEvent();
			readEv.setHash(sid);
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			readEv.maxread = cfg.getIntProperty("C4", "MaxReadBytes", 65536);
			readEv.timeout = cfg.getIntProperty("C4", "ReadTimeout", 25);
			KeyValuePair<String, String> addition = new KeyValuePair<String, String>(
			        "C4MiscInfo", readEv.timeout + "_" + readEv.maxread);
			HttpClientHandler h = writeEvent(new Event[] { wrapEvent(ev),
			        readEv }, "pull", read, addition);
			read.httpClient = h;
			EventHelper.resetEncodedEvent(ev);
			read.cacheEvent = ev;
		}
		else
		{
			// May be not working if uploading big data
			startWriteTask(ev);
			// startReadTask();
		}
	}

	public void close()
	{
		if (null != read)
		{
			read.stop();
			read = null;
		}
		closed = true;
		// logger.info(String.format("Session[%d] close http tunnels.", sid));
	}
}
