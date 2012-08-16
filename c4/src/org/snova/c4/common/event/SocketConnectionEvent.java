/**
 * 
 */
package org.snova.c4.common.event;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.c4.common.C4Constants;

/**
 * @author qiyingwang
 *
 */
@EventType(C4Constants.EVENT_TCP_CONNECTION_TYPE)
@EventVersion(1)
public class SocketConnectionEvent extends Event
{

	@Override
    protected boolean onDecode(Buffer buffer)
    {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    protected boolean onEncode(Buffer buffer)
    {
	    // TODO Auto-generated method stub
	    return false;
    }

}
