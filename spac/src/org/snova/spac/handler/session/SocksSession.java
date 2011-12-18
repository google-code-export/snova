/**
 * 
 */
package org.snova.spac.handler.session;

import org.arch.event.Event;
import org.arch.event.EventHeader;

/**
 * @author wqy
 *
 */
public class SocksSession extends Session
{
	public SocksSession(String target)
	{
		
	}
	@Override
    public SessionType getType()
    {
		return SessionType.SOCKS;
    }

	@Override
    public void handleEvent(EventHeader header, Event event)
    {
	    // TODO Auto-generated method stub
	    
    }
	
}
