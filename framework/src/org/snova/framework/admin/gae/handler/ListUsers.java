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
import org.snova.framework.admin.gae.auth.User;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.gae.AdminResponseEvent;
import org.snova.framework.event.gae.ListUserRequestEvent;
import org.snova.framework.event.gae.ListUserResponseEvent;
import org.snova.framework.proxy.gae.GAERemoteHandler;

/**
 *
 */
public class ListUsers implements CommandHandler
{
	public static final String	COMMAND	= "users";
	
	private GAERemoteHandler	connection;
	
	public ListUsers(GAERemoteHandler connection)
	{
		this.connection = connection;
	}
	
	@Override
	public void execute(String[] args)
	{
		final String formater = "%12s%12s%12s%24s";
		String header = String.format(formater, "Username", "Password",
		        "Group", "Blacklist");
		GAEAdmin.outputln(header);
		ListUserRequestEvent event = new ListUserRequestEvent();
		EventHandler handler = new EventHandler()
		{
			@Override
			public void onEvent(EventHeader header, Event event)
			{
				if (header.type == CommonEventConstants.USER_LIST_RESPONSE_EVENT_TYPE)
				{
					ListUserResponseEvent res = (ListUserResponseEvent) event;
					for (User line : res.users)
					{
						String output = String.format(formater,
						        line.getEmail(), line.getPasswd(),
						        line.getGroup(), line.getBlacklist());
						GAEAdmin.outputln(output);
					}
				}
				else if (event instanceof AdminResponseEvent)
				{
					AdminResponseEvent ev = (AdminResponseEvent) event;
					GAEAdmin.outputln(ev.response != null ? ev.response
					        : ev.errorCause);
				}
				synchronized (this)
				{
					this.notify();
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
		
	}
	
}
