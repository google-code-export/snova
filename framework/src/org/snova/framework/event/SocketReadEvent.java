/**
 * 
 */
package org.snova.framework.event;

import java.io.IOException;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;

/**
 * @author qiyingwang
 * 
 */
@EventType(CommonEventConstants.EVENT_SOCKET_READ_TYPE)
@EventVersion(1)
public class SocketReadEvent extends Event
{
	public int timeout;
	public int maxread;

	@Override
	protected boolean onDecode(Buffer buffer)
	{
		try
		{
			timeout = BufferHelper.readVarInt(buffer);
			maxread = BufferHelper.readVarInt(buffer);
			return true;
		}
		catch (IOException e)
		{
			return false;
		}
	}

	@Override
	protected boolean onEncode(Buffer buffer)
	{
		BufferHelper.writeVarInt(buffer, timeout);
		BufferHelper.writeVarInt(buffer, maxread);
		return true;
	}

}
