/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ChangePasswd.java 
 *
 * @author yinqiwen [ 2010-4-9 | 08:48:34 PM]
 *
 */
package org.snova.framework.admin.gae.handler;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.snova.framework.admin.gae.GAEAdmin;
import org.snova.framework.admin.gae.auth.Operation;
import org.snova.framework.admin.gae.auth.User;
import org.snova.framework.event.gae.UserOperationEvent;
import org.snova.framework.proxy.gae.GAERemoteHandler;

/**
 *
 */
public class ChangePasswd implements CommandHandler
{
	public static final String COMMAND = "passwd";

	private Options options = new Options();

	private String username;
	private GAERemoteHandler connection;

	public ChangePasswd(GAERemoteHandler connection, String username)
	{
		this.connection = connection;
		this.username = username;
		options.addOption("h", "help", false, "print this message.");
	}

	@Override
	public void execute(String[] args)
	{
		CommandLineParser parser = new PosixParser();

		try
		{
			// parse the command line arguments
			CommandLine line = parser.parse(options, args);

			// validate that block-size has been set
			if (line.hasOption("h"))
			{
				printHelp();
			}
			else
			{
				String[] passwdargs = line.getArgs();
				if (passwdargs != null && passwdargs.length > 1)
				{
					System.out.println(passwdargs.length);
					GAEAdmin.outputln("Only one arg expected!"
					        + Arrays.toString(passwdargs));
				}
				else
				{
					String modifyUser = username;
					if (passwdargs != null && passwdargs.length == 1)
					{
						modifyUser = passwdargs[0];
					}
					GAEAdmin.outputln("Enter the new password");
					GAEAdmin.output("New password:");
					String newpass = new String(System.console().readPassword())
					        .trim();
					UserOperationEvent event = new UserOperationEvent();
					event.opr = Operation.MODIFY;
					User user = new User();
					user.setEmail(modifyUser);
					user.setPasswd(newpass);
					event.user = user;
					AdminResponseEventHandler.syncSendEvent(connection, event);
				}

			}

		}
		catch (Exception exp)
		{
			exp.printStackTrace();
			System.out.println("Error:" + exp.getMessage());
		}

		// formatter.p
	}

	@Override
	public void printHelp()
	{
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(COMMAND + " [username]", options);

	}

}
