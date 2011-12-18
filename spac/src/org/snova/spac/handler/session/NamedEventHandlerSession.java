/**
 * 
 */
package org.snova.spac.handler.session;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.NamedEventHandler;

/**
 * @author wqy
 *
 */
public class NamedEventHandlerSession  extends Session
{
	private NamedEventHandler handler;
	
	public NamedEventHandlerSession(NamedEventHandler handler)
	{
		this.handler = handler;
	}

	@Override
    public SessionType getType()
    {
	    return SessionType.NAMED_HANDLER;
    }

	@Override
    public void handleEvent(EventHeader header, Event event)
    {
		handler.onEvent(header, event);
    }
	
}
