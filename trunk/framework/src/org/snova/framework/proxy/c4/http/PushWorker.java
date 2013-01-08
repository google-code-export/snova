/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;

/**
 * @author qiyingwang
 * 
 */
public class PushWorker extends FutureCallback.FutureCallbackAdapter
{
	boolean isReady = true;
	int waitTime = 1;
	Buffer cachedBuffer = null;
	HttpTunnelService serv;
	int index;

	public PushWorker(HttpTunnelService serv, int index)
	{
		this.serv = serv;
		this.index = index;
	}

	public void start(Buffer buf)
	{
		isReady = false;
		cachedBuffer = buf;
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, serv.server.url.toString() + "push");

		int port = 80;
		if (serv.server.url.getPort() > 0)
		{
			port = serv.server.url.getPort();
		}
		else
		{
			if (serv.server.url.getScheme().equalsIgnoreCase("https"))
			{
				port = 443;
			}
		}
		request.setHeader(HttpHeaders.Names.HOST, serv.server.url.getHost()
		        + ":" + port);

		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		request.setHeader("UserToken", NetworkHelper.getMacAddress());
		request.setHeader("C4MiscInfo", String.format("%d_%d", index, 25));
		request.setHeader(
		        HttpHeaders.Names.USER_AGENT,
		        cfg.getProperty("C4", "UserAgent",
		                "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:15.0) Gecko/20100101 Firefox/15.0.1"));
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
		        "application/octet-stream");
		request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
		request.setContent(ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
		        buf.getReadIndex(), buf.readableBytes()));
		HttpClient client = HttpTunnelService.httpClient;
		try
		{
			client.execute(request, this);
		}
		catch (HttpClientException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onResponse(HttpResponse res)
	{
		if (res.getStatus().getCode() >= 500)
		{
			System.out.println(res.getContent().toString(
			        Charset.forName("utf8")));
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			{
				@Override
				public void run()
				{
					start(cachedBuffer);
				}
			}, waitTime, TimeUnit.SECONDS);
			waitTime *= 2;
		}
		else
		{
			cachedBuffer = null;
			isReady = true;
			serv.tryWriteEvent(index);
		}
	}

	@Override
	public void onError(String error)
	{
		System.out.println("###############" + error);
		SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
		{
			public void run()
			{
				start(cachedBuffer);
			}
		}, waitTime, TimeUnit.SECONDS);
		waitTime *= 2;
	}
}
