/**
 * 
 */
package org.snova.c4.common.event;

import java.io.IOException;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.c4.common.C4Constants;

/**
 * @author qiyingwang
 * 
 */
@EventType(C4Constants.EVENT_RSOCKET_ACCEPTED_TYPE)
@EventVersion(1)
public class RSocketAcceptedEvent extends Event
{
	public String domain;
	public int port = 80;

	@Override
	protected boolean onDecode(Buffer buf)
	{
		try
		{
			domain = BufferHelper.readVarString(buf);
			port = BufferHelper.readVarInt(buf);
			return true;
		}
		catch (IOException e)
		{
			return false;
		}
	}

	@Override
	protected boolean onEncode(Buffer buf)
	{
		BufferHelper.writeVarString(buf, domain);
		BufferHelper.writeVarInt(buf, port);
		return true;
	}

}
