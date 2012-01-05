/**
 * 
 */
package org.snova.heroku.common.event;

import java.io.IOException;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.heroku.common.HerokuConstants;

/**
 * @author wqy
 *
 */
@EventType(HerokuConstants.EVENT_SEQUNCEIAL_CHUNK_TYPE)
@EventVersion(1)
public class SequentialChunkEvent extends Event
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
	        buffer.read(content);
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
