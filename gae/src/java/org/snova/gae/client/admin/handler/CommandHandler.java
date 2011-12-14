/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: CommandHandler.java 
 *
 * @author yinqiwen [ 2010-4-9 | 08:29:43 PM]
 *
 */
package org.snova.gae.client.admin.handler;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.snova.gae.client.admin.GAEAdmin;
import org.snova.gae.client.connection.ProxyConnection;
import org.snova.gae.common.event.AdminResponseEvent;
import org.snova.gae.common.event.ServerConfigEvent;

/**
 *
 */
public interface CommandHandler
{
	
	
	public static class AdminResponseEventHandler implements EventHandler
	{
		public static void syncSendEvent(ProxyConnection conn, Event ev)
		{
			AdminResponseEventHandler handler = new AdminResponseEventHandler();
			if(conn.send(ev, handler))
			{
				synchronized (handler)
                {
					try
                    {
	                    handler.wait(60*1000);
                    }
                    catch (InterruptedException e)
                    {
	                   
                    }
                }
			}
		}
		
		@Override
        public void onEvent(EventHeader header, Event event)
        {
			synchronized(this)
			{
				this.notify();
			}
			if(event instanceof AdminResponseEvent)
			{
				AdminResponseEvent ev = (AdminResponseEvent) event;
				GAEAdmin.outputln(ev.errorCause != null?ev.errorCause:ev.response); 
			}
			else if(event instanceof ServerConfigEvent)
			{
				ServerConfigEvent ev = (ServerConfigEvent) event;
				ev.cfg.print(System.out);
			}
        }
		
	}
	
	public void execute(String[] args);
	
	public void printHelp();
}
