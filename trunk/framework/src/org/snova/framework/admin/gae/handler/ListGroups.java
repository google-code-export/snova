/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ListUsers.java 
 *
 * @author yinqiwen [ 2010-4-9 | 08:49:51 PM]
 *
 */
package org.snova.framework.admin.gae.handler;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.snova.framework.admin.gae.GAEAdmin;
import org.snova.framework.admin.gae.auth.Group;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.gae.AdminResponseEvent;
import org.snova.framework.event.gae.ListGroupRequestEvent;
import org.snova.framework.event.gae.ListGroupResponseEvent;
import org.snova.framework.proxy.gae.GAERemoteHandler;

/**
 *
 */
public class ListGroups implements CommandHandler
{
	public static final String	COMMAND	= "groups";
	
	private GAERemoteHandler	connection;
	
	public ListGroups(GAERemoteHandler connection)
	{
		this.connection = connection;
	}
	
	@Override
	public void execute(String[] args)
	{
		ListGroupRequestEvent event = new ListGroupRequestEvent();
		final String formater = "%20s%20s";
		String header = String.format(formater, "Group", "Blacklist");
		GAEAdmin.outputln(header);
		EventHandler handler = new EventHandler()
		{
			@Override
			public void onEvent(EventHeader header, Event event)
			{
				synchronized (this)
				{
					this.notify();
				}
				if (header.type == CommonEventConstants.GROUOP_LIST_RESPONSE_EVENT_TYPE)
				{
					ListGroupResponseEvent res = (ListGroupResponseEvent) event;
					for (Group line : res.groups)
					{
						String output = String.format(formater, line.getName(),
						        line.getBlacklist());
						GAEAdmin.outputln(output);
					}
				}
				else if (event instanceof AdminResponseEvent)
				{
					AdminResponseEvent ev = (AdminResponseEvent) event;
					GAEAdmin.outputln(ev.response != null ? ev.response
					        : ev.errorCause);
				}
			}
		};
		connection.requestEvent(event, handler);
		synchronized (handler)
		{
			try
			{
				handler.wait(60 * 1000);
			}
			catch (InterruptedException e)
			{
				
			}
		}
	}
	
	@Override
	public void printHelp()
	{
		// TODO Auto-generated method stub
		
	}
	
}
