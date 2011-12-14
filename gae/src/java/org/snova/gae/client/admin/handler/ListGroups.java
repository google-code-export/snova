/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ListUsers.java 
 *
 * @author yinqiwen [ 2010-4-9 | 08:49:51 PM]
 *
 */
package org.snova.gae.client.admin.handler;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.snova.gae.client.admin.GAEAdmin;
import org.snova.gae.client.connection.ProxyConnection;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.auth.Group;
import org.snova.gae.common.event.AdminResponseEvent;
import org.snova.gae.common.event.ListGroupRequestEvent;
import org.snova.gae.common.event.ListGroupResponseEvent;


/**
 *
 */
public class ListGroups implements CommandHandler
{
	public static final String COMMAND = "groups";
	
	private ProxyConnection connection;
	public ListGroups(ProxyConnection connection)
	{
		this.connection = connection;
	}
	
	@Override
	public void execute(String[] args)
	{
		ListGroupRequestEvent event = new ListGroupRequestEvent();
		final String formater = "%20s%20s";
		String header = String.format(formater,  "Group", "Blacklist");
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
				if (header.type == GAEConstants.GROUOP_LIST_RESPONSE_EVENT_TYPE)
				{
					ListGroupResponseEvent res = (ListGroupResponseEvent) event;
					for(Group line:res.groups)
					{
						String output = String.format(formater, line.getName(), line.getBlacklist());
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
		if (connection.send(event, handler))
		{
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
	}

	@Override
	public void printHelp()
	{
		// TODO Auto-generated method stub
		
	}

}
