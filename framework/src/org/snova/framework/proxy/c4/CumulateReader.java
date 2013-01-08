/**
 * 
 */
package org.snova.framework.proxy.c4;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHeader;
import org.jboss.netty.buffer.ChannelBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author qiyingwang
 * 
 */
public class CumulateReader
{
	protected static Logger logger = LoggerFactory
	        .getLogger(CumulateReader.class);
	private Buffer resBuffer = new Buffer(256);
	private int chunkLength = -1;

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
							// logger.error("Unexpected event received since session closed:"
							// + ev.getHash());
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

	public void fillResponseBuffer(ChannelBuffer buffer)
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
}
