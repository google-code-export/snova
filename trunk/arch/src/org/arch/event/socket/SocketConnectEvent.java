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
@EventType(SocketEventContants.SOCKET_CONNECT_EVENT_TYPE)
@EventVersion(1)
public  class SocketConnectEvent extends Event
{
	public static final int OPEND = 1;
	public static final int CLOSED = 2;
	
	public String host;
	public int port;
	public SocketConnectEvent(){}
	public SocketConnectEvent(String host, int port)
    {
	    this.host = host;
	    this.port = port;
    }

	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
        {
			host = BufferHelper.readVarString(buffer);
			port = BufferHelper.readVarInt(buffer);
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
		BufferHelper.writeVarString(buffer, host);
		BufferHelper.writeVarInt(buffer, port);
	    return true;
    }
	

}
