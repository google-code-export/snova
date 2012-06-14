/**
 * 
 */
package org.arch.event.socket;

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
@EventType(SocketEventContants.SOCKET_CLOSE_EVENT_TYPE)
@EventVersion(1)
public  class SocketCloseEvent extends Event
{

	public SocketCloseEvent(){}

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
