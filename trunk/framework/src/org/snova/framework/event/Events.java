/**
 * 
 */
package org.snova.framework.event;

import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.NamedEventHandler;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.FrameworkConfiguration;

/**
 * @author qiyingwang
 *
 */
public class Events
{
	
	public static class NameDispatchEventHandler implements EventHandler
	{
		protected Logger logger = LoggerFactory.getLogger(getClass());
		FrameworkConfiguration config;
		public NameDispatchEventHandler(FrameworkConfiguration config)
		{
			this.config = config;
		}
		@Override
        public void onEvent(EventHeader header, Event event)
        {
			String name = config.getProxyEventHandler();
	        NamedEventHandler handler = EventDispatcher.getSingletonInstance().getNamedEventHandler(name);
	        if(null != handler)
	        {
	        	handler.onEvent(header, event);
	        }
	        else
	        {
	        	logger.error("No named event handler found with name:" + name);
	        }
	        
        }
		
	}
	public static void init(FrameworkConfiguration config)
	{
		try
        {
			NameDispatchEventHandler handler = new NameDispatchEventHandler(config);
	        EventDispatcher.getSingletonInstance().register(HTTPRequestEvent.class, handler);
	        EventDispatcher.getSingletonInstance().register(HTTPChunkEvent.class, handler);
			EventDispatcher.getSingletonInstance().register(HTTPConnectionEvent.class, handler);
			
        }
        catch (Exception e)
        {
	        //
        }	
	}
}
