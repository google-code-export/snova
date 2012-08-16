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
@EventType(C4Constants.EVENT_TCP_CHUNK_TYPE)
@EventVersion(1)
public class TCPChunkEvent extends Event
{
	public int sequence;
	public byte[] content = new byte[0];
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
        {
	        sequence = BufferHelper.readVarInt(buffer);
	        int size = BufferHelper.readVarInt(buffer);
	        content = new byte[size];
	        if(size > 0)
	        {
	        	buffer.read(content);
	        }
        }
        catch (IOException e)
        {
	        return false;
        }
	    return true;
    }

	@Override
    protected boolean onEncode(Buffer buffer)
    {
		BufferHelper.writeVarInt(buffer, sequence);
		BufferHelper.writeVarInt(buffer, content.length);
		if(content.length > 0)
		{
			buffer.write(content);
		}
	    return true;
    }
	
}
