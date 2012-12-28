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
@EventType(CommonEventConstants.EVENT_TCP_CONNECTION_TYPE)
@EventVersion(1)
public class SocketConnectionEvent extends Event
{
	public static final int TCP_CONN_OPENED = 1;
	public static final int TCP_CONN_CLOSED = 2;
	
	public int status;
	public String addr;

	@Override
	protected boolean onDecode(Buffer buffer)
	{
		try
		{
			status = BufferHelper.readVarInt(buffer);
			addr = BufferHelper.readVarString(buffer);
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
		BufferHelper.writeVarInt(buffer, status);
		BufferHelper.writeVarString(buffer, addr);
		return true;
	}

}
