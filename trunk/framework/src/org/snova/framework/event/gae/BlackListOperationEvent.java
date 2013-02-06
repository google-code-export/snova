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
import org.snova.framework.event.CommonEventConstants;

/**
 * @author qiyingwang
 *
 */
@EventType(CommonEventConstants.BLACKLIST_OPERATION_EVENT_TYPE)
@EventVersion(1)
public class BlackListOperationEvent extends Event
{
	public String username;
	public String groupname;
	public String host;
	public Operation opr;
	@Override
    protected boolean onDecode(Buffer buffer)
    {
		try
		{
			username = BufferHelper.readVarString(buffer);
			groupname = BufferHelper.readVarString(buffer);
			host = BufferHelper.readVarString(buffer);
			opr = Operation.fromInt(BufferHelper.readVarInt(buffer));
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
		BufferHelper.writeVarString(buffer, username);
		BufferHelper.writeVarString(buffer, groupname);
		BufferHelper.writeVarString(buffer, host);
		BufferHelper.writeVarInt(buffer, opr.getValue());
	    return true;
    }
}
