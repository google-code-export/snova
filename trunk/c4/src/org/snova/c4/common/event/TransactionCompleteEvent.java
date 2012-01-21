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
 * @author wqy
 *
 */
@EventType(C4Constants.EVENT_TRANSACTION_COMPLETE_TYPE)
@EventVersion(1)
public class TransactionCompleteEvent extends Event
{

	@Override
    protected boolean onDecode(Buffer arg0)
    {
	    return true;
    }

	@Override
    protected boolean onEncode(Buffer arg0)
    {
		 return true;
    }
	
}
