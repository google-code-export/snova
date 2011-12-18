/**
 * 
 */
package org.snova.spac.handler.session;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.jboss.netty.channel.Channel;

/**
 * @author wqy
 *
 */
public class ForwardSession extends Session
{
	private String host;
	private int port;
	
	private Channel forwardChannel;
	
	public ForwardSession(String addr)
	{
		
	}
	@Override
    public SessionType getType()
    {
	    // TODO Auto-generated method stub
	    return SessionType.FORWARD;
    }

	@Override
    public void handleEvent(EventHeader header, Event event)
    {
	    // TODO Auto-generated method stub
	    
    }
	
}
