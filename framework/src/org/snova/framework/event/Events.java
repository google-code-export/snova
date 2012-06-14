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
import org.arch.event.socket.SocketCloseEvent;
import org.arch.event.socket.SocketConnectEvent;
import org.arch.event.socket.SocketDataEvent;
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
		String handlerName;
		public NameDispatchEventHandler(FrameworkConfiguration config)
		{
			this.config = config;
		}
		public NameDispatchEventHandler(String name)
		{
			this.handlerName = name;
		}
		@Override
        public void onEvent(EventHeader header, Event event)
        {
			String name = config.getProxyEventHandler();
			if(handlerName != null)
			{
				name = handlerName;
			}
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
			
			NameDispatchEventHandler c4 = new NameDispatchEventHandler("C4");
			EventDispatcher.getSingletonInstance().register(SocketDataEvent.class, c4);
	        EventDispatcher.getSingletonInstance().register(SocketConnectEvent.class, c4);
			EventDispatcher.getSingletonInstance().register(SocketCloseEvent.class, c4);
        }
        catch (Exception e)
        {
	        //
        }	
	}
}
