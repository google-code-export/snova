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
@EventType(HerokuConstants.EVENT_REST_REQEUST_TYPE)
@EventVersion(1)
public class EventRestRequest extends Event
{
	
	@Override
	protected boolean onDecode(Buffer buffer)
	{
		return true;
	}
	
	@Override
	protected boolean onEncode(Buffer buffer)
	{
		return true;
	}
	
}
