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
 * @author wqy
 *
 */
@EventType(C4Constants.EVENT_REST_NOTIFY_TYPE)
@EventVersion(1)
public class EventRestNotify extends Event
{
	public int rest;
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
        {
	        rest = BufferHelper.readVarInt(buffer);
	        return true;
        }
        catch (IOException e)
        {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }
	    return false;
    }

	@Override
    protected boolean onEncode(Buffer buffer)
    {
		BufferHelper.writeVarInt(buffer, rest);
	    return true;
    }
	
}
