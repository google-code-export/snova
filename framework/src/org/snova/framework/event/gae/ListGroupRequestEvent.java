/**
 * 
 */
package org.snova.framework.event.gae;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.framework.event.CommonEventConstants;

/**
 * @author qiyingwang
 *
 */
@EventType(CommonEventConstants.GROUOP_LIST_REQUEST_EVENT_TYPE)
@EventVersion(1)
public class ListGroupRequestEvent extends Event
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
