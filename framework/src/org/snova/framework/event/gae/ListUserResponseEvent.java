/**
 * 
 */
package org.snova.framework.event.gae;

import java.io.IOException;
import java.util.List;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.framework.admin.gae.auth.User;
import org.snova.framework.event.CommonEventConstants;

/**
 * @author qiyingwang
 *
 */
@EventType(CommonEventConstants.USER_LIST_RESPONSE_EVENT_TYPE)
@EventVersion(1)
public class ListUserResponseEvent extends Event
{
	public List<User> users;
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
        {
			users = BufferHelper.readList(buffer, User.class);
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
		BufferHelper.writeList(buffer, users);
		return true;
    }
}
