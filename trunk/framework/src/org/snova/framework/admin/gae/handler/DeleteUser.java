/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: DelUser.java 
 *
 * @author yinqiwen [ 2010-4-9 | 11:38:31 PM]
 *
 */
package org.snova.framework.admin.gae.handler;

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
public class DeleteUser  implements CommandHandler
{
	public static final String	COMMAND	= "userdel";
	private Options				options	= new Options();
	

	private GAERemoteHandler connection;
	public DeleteUser(GAERemoteHandler connection)
	{
		this.connection = connection;
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
			if(line.hasOption("h"))
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp(COMMAND + " username", options);
			}
			else
			{
				String[] usernameargs = line.getArgs();
				if(usernameargs != null && usernameargs.length != 1)
				{
					GAEAdmin.outputln("Argument username required!");
				}
				UserOperationEvent event = new UserOperationEvent();
				event.opr = Operation.DELETE;
				User user = new User();
				user.setEmail(usernameargs[0]);
				event.user = user;
				AdminResponseEventHandler.syncSendEvent(connection, event);
			}
		}
		catch(Exception exp)
		{
			System.out.println("Error:" + exp.getMessage());
		}
		
	}

	@Override
	public void printHelp()
	{
		// TODO Auto-generated method stub
		
	}
	
}
