/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffer;
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
import org.snova.framework.proxy.c4.C4RemoteHandler;
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
	private Buffer resBuffer = new Buffer(256);
	private int chunkLength = -1;
	private int waitTime = 1;
	private long startTime;
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
			if (serv.server.url.getProtocol().equalsIgnoreCase("https"))
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

	private boolean tryHandleBuffer()
	{
		if (chunkLength == -1)
		{
			if (resBuffer.readableBytes() >= 4)
			{
				chunkLength = BufferHelper.readFixInt32(resBuffer, true);
			}
			else
			{
				return false;
			}
		}
		if (chunkLength > 0)
		{
			if (resBuffer.readableBytes() < chunkLength)
			{
				return false;
			}
			Buffer content = new Buffer(chunkLength);
			content.write(resBuffer, chunkLength);
			try
			{
				while (content.readable())
				{
					Event ev = EventDispatcher.getSingletonInstance().parse(
					        content);
					ev = Event.extractEvent(ev);
					EventHeader header = Event.getHeader(ev);
					try
					{
						C4RemoteHandler session = C4RemoteHandler.getSession(ev
						        .getHash());
						if (null != session)
						{
							session.onEvent(header, ev);
						}
						else
						{
							//logger.error("Unexpected event received since session closed:" + ev.getHash());
						}
					}
					catch (Exception e)
					{
						logger.error("Ignore event handle exception ", e);
					}

				}
			}
			catch (Exception e)
			{
				logger.error("Failed to parse recv content", e);
			}

			resBuffer.discardReadedBytes();
			chunkLength = -1;
			return true;
		}
		return false;
	}

	private void fillResponseBuffer(ChannelBuffer buffer)
	{
		int contentlen = buffer.readableBytes();
		startTime = System.currentTimeMillis();
		if (contentlen > 0)
		{
			resBuffer.ensureWritableBytes(contentlen);
			buffer.readBytes(resBuffer.getRawBuffer(),
			        resBuffer.getWriteIndex(), contentlen);
			resBuffer.advanceWriteIndex(contentlen);
			while (resBuffer.readable() && tryHandleBuffer())
				;
		}
	}

	@Override
	public void onResponse(HttpResponse res)
	{
		if (res.getStatus().getCode() == 200)
		{
			fillResponseBuffer(res.getContent());

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
		fillResponseBuffer(chunk.getContent());
	}

	@Override
	public void onComplete(HttpResponse res)
	{
		start();

	}

	@Override
	public void onError(String error)
	{
		start();
	}
}
