/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.util.concurrent.TimeUnit;

import org.arch.config.IniProperties;
import org.arch.util.NetworkHelper;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.CumulateReader;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;
import org.snova.http.client.HttpClientHandler;

/**
 * @author qiyingwang
 * 
 */
public class PullWorker implements FutureCallback
{
	protected static Logger logger = LoggerFactory.getLogger(PullWorker.class);
	private HttpTunnelService serv;
	HttpClientHandler httpClientHandler;
	private CumulateReader cumulater = new CumulateReader();
	private int index;

	public PullWorker(HttpTunnelService serv, int index)
	{
		this.serv = serv;
		this.index = index;
	}

	public void start()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
		        HttpMethod.POST, serv.server.url.toString() + "pull");
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
		request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
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
		if (res.getStatus().getCode() == 200)
		{
			cumulater.fillResponseBuffer(res.getContent());
		}
		else
		{
			if (res.getStatus().getCode() != 200)
			{
				System.out.println("##########" + res.getStatus());
			}
		}

	}

	@Override
	public void onBody(HttpChunk chunk)
	{
		cumulater.fillResponseBuffer(chunk.getContent());
	}

	@Override
	public void onComplete(HttpResponse res)
	{
		start();
	}

	@Override
	public void onError(String error)
	{
		SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
		{
			public void run()
			{
				start();
			}
		}, 1, TimeUnit.SECONDS);
	}
}
