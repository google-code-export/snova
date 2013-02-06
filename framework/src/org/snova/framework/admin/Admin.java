/**
 * This file is part of the hyk-proxy-framework project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Admin.java 
 *
 * @author yinqiwen [ 2010-9-4 | 10:44:32 PM ]
 *
 */
package org.snova.framework.admin;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

import org.snova.framework.admin.gae.GAEAdmin;
import org.snova.framework.common.Version;
import org.snova.framework.event.CommonEvents;
import org.snova.framework.launch.ApplicationLauncher;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.trace.TUITrace;
import org.snova.framework.util.SharedObjectHelper;

/**
 *
 */
public class Admin
{

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException
	{
		ApplicationLauncher.initLoggerConfig();
		SharedObjectHelper.setTrace(new TUITrace());
		HostsService.init();
		CommonEvents.init(null, false);

		Console console = System.console();
		while (true)
		{
			System.out.println("==============Snova V" + Version.value
			        + "===========");
			System.out.println("[1] GAE");
			System.out.println("[2] C4");
			System.out.println("[3] SPAC");
			System.out.println("[0] Exit");
			System.out.print("Please enter 0-3:");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String s = reader.readLine();
			try
			{
				int choice = Integer.parseInt(s);
				if (choice >= 0 && choice <= 3)
				{
					if (choice == 0)
					{
						System.exit(1);
					}
					switch (choice)
					{
						case 1:
						{
							new GAEAdmin().run();
							break;
						}
						default:
						{
							break;
						}
					}
					continue;
				}
			}
			catch (Exception e)
			{
				//
			}
			System.err.println("Wrong input:" + s);
		}

	}
}
