/**
 * 
 */
package org.snova.framework.event;

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
@EventType(CommonEventConstants.EVENT_USER_LOGIN_TYPE)
@EventVersion(1)
public class UserLoginEvent extends Event
{
	public String user;
	@Override
    protected boolean onDecode(Buffer buf)
    {
		try
		{
			user = BufferHelper.readVarString(buf);
			return true;
		}
		catch (IOException e)
		{
			return false;
		}
    }

	@Override
    protected boolean onEncode(Buffer buf)
    {
		BufferHelper.writeVarString(buf, user);
	    return true;
    }

}
