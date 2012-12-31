/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClientHandler;

/**
 * @author yinqiwen
 * 
 */
public class HttpReadHandlerCallback implements FutureCallback
{
	protected static Logger logger = LoggerFactory
	        .getLogger(HttpReadHandlerCallback.class);
	Event cacheEvent;
	private HttpDualConn conn;
	HttpClientHandler httpClient;
	private Buffer resBuffer = new Buffer(256);
	private int chunkLength = -1;
	private int waitTime = 1;

	public HttpReadHandlerCallback(HttpDualConn httpDualConn)
	{
		conn = httpDualConn;
	}

	void stop()
	{
		if (null != httpClient)
		{
			httpClient.closeChannel();
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
	public synchronized void onBody(HttpChunk chunk)
	{
		fillResponseBuffer(chunk.getContent());
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
					conn.cb.onEvent(header, ev);
				}
			}
			catch (Exception e)
			{
				logger.error("Failed to parse recv content", e);
			}

			resBuffer.discardReadedBytes();
			chunkLength = -1;
		}
		return true;
	}

	private void fillResponseBuffer(ChannelBuffer buffer)
	{
		int contentlen = buffer.readableBytes();

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

	private void retry()
	{
		if (null != cacheEvent)
		{
			conn.startWriteTask(cacheEvent);
		}
		conn.startReadTask();
	}

	@Override
	public void onComplete(HttpResponse res)
	{
		// logger.info(String.format("Session[%d] read tunnel closed",
		// conn.sid));
		conn.read = null;
		if (res.getStatus().getCode() != 200)
		{
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
			{

				@Override
				public void run()
				{
					retry();
				}
			}, waitTime, TimeUnit.SECONDS);
			waitTime *= 2;
		}
		else
		{
			waitTime = 1;
			cacheEvent = null;
			retry();
		}
	}

	@Override
	public void onError(String error)
	{
		// logger.error(String.format("Session[%d] read tunnel ecounter error %s",
		// conn.sid, error));
		conn.read = null;
		SharedObjectHelper.getGlobalTimer().schedule(new Runnable()
		{
			@Override
			public void run()
			{
				retry();
			}
		}, waitTime, TimeUnit.SECONDS);
		waitTime *= 2;

	}
}
