/**
 * 
 */
package org.snova.heroku.common.event;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
@EventType(HerokuConstants.EVENT_REST_REQEUST_TYPE)
@EventVersion(1)
public class EventRestRequest extends Event
{
	public List<Integer>	restSessions	= new LinkedList<Integer>();
	
	@Override
	protected boolean onDecode(Buffer buffer)
	{
		try
		{
			restSessions.clear();
			int size = BufferHelper.readVarInt(buffer);
			for (int i = 0; i < size; i++)
			{
				int tmp = BufferHelper.readVarInt(buffer);
				restSessions.add(tmp);
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
		BufferHelper.writeVarInt(buffer, restSessions.size());
		for (Integer id : restSessions)
		{
			BufferHelper.writeVarInt(buffer, id);
		}
		return true;
	}
	
}
