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
import org.snova.framework.admin.gae.auth.Operation;
import org.snova.framework.admin.gae.auth.User;
import org.snova.framework.event.CommonEventConstants;

/**
 * @author qiyingwang
 *
 */
@EventType(CommonEventConstants.USER_OPERATION_EVENT_TYPE)
@EventVersion(1)
public class UserOperationEvent extends Event
{
	public User user;
	public Operation opr;
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		user = new User();
		if(user.decode(buffer))
		{
			try
            {
	            opr = Operation.fromInt(BufferHelper.readVarInt(buffer));
            }
            catch (IOException e)
            {
	            return false;
            }
			return true;
		}
	    return false;
    }

	@Override
    protected boolean onEncode(Buffer buffer)
    {
		user.encode(buffer);
		BufferHelper.writeVarInt(buffer, opr.getValue());
		return true;
    }
}
