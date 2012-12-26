/**
 * 
 */
package org.snova.framework.event.gae;

import java.io.IOException;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.framework.event.CommonEventConstants;
/**
 * @author qiyingwang
 *
 */
@EventType(CommonEventConstants.AUTH_REQUEST_EVENT_TYPE)
@EventVersion(1)
public class AuthRequestEvent extends Event
{
	public static final int AUTH_SUCCESS = 1;
	public static final int AUTH_FAIELD = -1;
	public static final int AUTH_TRANSPORT_FAILED = -2;
	
	public String appid;
	public String user;
	public String passwd;
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
        {
	        appid = BufferHelper.readVarString(buffer);
	        user = BufferHelper.readVarString(buffer);
			passwd = BufferHelper.readVarString(buffer);
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
	    BufferHelper.writeVarString(buffer, appid);
	    BufferHelper.writeVarString(buffer, user);
	    BufferHelper.writeVarString(buffer, passwd);
	    return true;
    }

}
