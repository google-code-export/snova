/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Admin.java 
 *
 * @author yinqiwen [ 2010-4-9 | 06:59:44 PM]
 *
 */
package org.snova.framework.admin.gae;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptType;
import org.arch.util.StringHelper;
import org.snova.framework.admin.gae.auth.User;
import org.snova.framework.admin.gae.handler.AddGroup;
import org.snova.framework.admin.gae.handler.AddUser;
import org.snova.framework.admin.gae.handler.Blacklist;
import org.snova.framework.admin.gae.handler.ChangePasswd;
import org.snova.framework.admin.gae.handler.ClearScreen;
import org.snova.framework.admin.gae.handler.CommandHandler;
import org.snova.framework.admin.gae.handler.DeleteGroup;
import org.snova.framework.admin.gae.handler.DeleteUser;
import org.snova.framework.admin.gae.handler.Exit;
import org.snova.framework.admin.gae.handler.Help;
import org.snova.framework.admin.gae.handler.ListGroups;
import org.snova.framework.admin.gae.handler.ListUsers;
import org.snova.framework.admin.gae.handler.ShareAppID;
import org.snova.framework.admin.gae.handler.UnShareAppID;
import org.snova.framework.common.Version;
import org.snova.framework.event.CommonEventConstants;
import org.snova.framework.event.gae.AuthRequestEvent;
import org.snova.framework.event.gae.AuthResponseEvent;
import org.snova.framework.proxy.gae.GAERemoteHandler;
import org.snova.framework.proxy.gae.GAEServerAuth;

/**
 *
 */
public class GAEAdmin implements Runnable
{
	private static final String PROMOTE = "$ ";

	private User userInfo;

	private Map<String, CommandHandler> handlers = new HashMap<String, CommandHandler>();
	
	private GAERemoteHandler connection;

	public static void err(String msg)
	{
		System.err.print(msg);
	}

	public static void errln(String msg)
	{
		System.err.println(msg);
	}

	public static void output(String msg)
	{
		if (null != msg)
		{
			System.out.print(msg);
		}
	}

	public static void outputln(String msg)
	{
		if (null != msg)
		{
			System.out.println(msg);
		}
	}

	public static void exit(String msg)
	{
		errln(msg);
		System.console().printf("Press <Enter> to exit...");
		System.console().readLine();
		System.exit(1);
	}

	protected void registerCommandHandler(String user)
	{
		handlers.put(Exit.COMMAND, new Exit());
		handlers.put(ClearScreen.COMMAND, new ClearScreen());
		handlers.put(ChangePasswd.COMMAND, new ChangePasswd(connection,user));
		handlers.put(AddUser.COMMAND, new AddUser(connection));
		handlers.put(AddGroup.COMMAND, new AddGroup(connection));
		handlers.put(ListUsers.COMMAND, new ListUsers(connection));
		handlers.put(ListGroups.COMMAND, new ListGroups(connection));
		handlers.put(DeleteUser.COMMAND, new DeleteUser(connection));
		handlers.put(DeleteGroup.COMMAND, new DeleteGroup(connection));
		handlers.put(Blacklist.COMMAND, new Blacklist(connection));
		handlers.put(UnShareAppID.COMMAND, new UnShareAppID(connection));
		handlers.put(ShareAppID.COMMAND, new ShareAppID(connection));
		handlers.put(Help.COMMAND, new Help());
	}

	public void run()
	{
		try
		{
			output("appid:");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String appid = reader.readLine();
			output("login:");
			String user = reader.readLine();
			output("password:");
			String passwd = new String(System.console().readPassword());
			final GAEServerAuth auth = new GAEServerAuth();
			auth.appid = appid;
			auth.user = user.trim();
			auth.passwd = passwd.trim();
			
			connection = new GAERemoteHandler(auth);
			AuthRequestEvent event = new AuthRequestEvent();
			event.appid = appid;
			event.user = user.trim();
			event.passwd = passwd.trim();
			connection.requestEvent(event, new EventHandler()
			{
				@Override
				public void onEvent(EventHeader header, Event event)
				{
					if (header.type == CommonEventConstants.AUTH_RESPONSE_EVENT_TYPE)
					{
						AuthResponseEvent res = (AuthResponseEvent) event;
						auth.token = res.token;
					}
					synchronized (connection)
					{
						connection.notify();
					}
				}
			});
			synchronized (connection)
			{
				try
				{
					connection.wait(60000);
				}
				catch (InterruptedException e)
				{
				}
			}
			if(StringHelper.isEmptyString(auth.token)){
				outputln("Invalid user/passwd.");
				return;
			}
			outputln("#####" + auth.token);
			this.userInfo = new User();
			userInfo.setEmail(user);
			userInfo.setPasswd(passwd);

			InputStream is = getClass().getResourceAsStream("welcome.txt");
			byte[] buffer = new byte[4096];
			int len = is.read(buffer);
			String format = new String(buffer, 0, len);
			outputln(String.format(format, Version.value, Help.USAGE,
					Version.value, Version.value));

			registerCommandHandler(user);

			while (true)
			{
				outputln(user + "@" + appid + " ~");
				output(PROMOTE);
				String[] commands = reader.readLine().split(
				        "\\s+");
				if (null == commands || commands.length == 0
				        || commands[0].trim().equals(""))
				{
					continue;
				}
				String[] args = new String[commands.length - 1];
				System.arraycopy(commands, 1, args, 0, args.length);
				CommandHandler handler = handlers.get(commands[0].trim());
				if (null == handler)
				{
					outputln("-admin: " + commands[0] + ": command not found");
					continue;
				}
				handler.execute(args);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			GAEAdmin.exit(e.getMessage());
		}
	}
}
