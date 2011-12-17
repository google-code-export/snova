/**
 * 
 */
package org.snova.gae.client.admin.handler;

import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.arch.event.Event;
import org.arch.event.EventHandler;
import org.arch.event.EventHeader;
import org.snova.gae.client.admin.GAEAdmin;
import org.snova.gae.client.admin.handler.CommandHandler.AdminResponseEventHandler;
import org.snova.gae.client.connection.ProxyConnection;
import org.snova.gae.common.GAEConstants;
import org.snova.gae.common.auth.Operation;
import org.snova.gae.common.auth.User;
import org.snova.gae.common.event.AdminResponseEvent;
import org.snova.gae.common.event.ListUserResponseEvent;
import org.snova.gae.common.event.ShareAppIDEvent;
import org.snova.gae.common.event.UserOperationEvent;

/**
 * @author qiyingwang
 * 
 */
public class ShareAppID implements CommandHandler
{
	public static final String	COMMAND	= "share";
	private Options	           options	= new Options();
	private ProxyConnection	   connection;
	
	public ShareAppID(ProxyConnection connection)
	{
		this.connection = connection;
		options.addOption("h", "help", false, "print this message.");
	}
	
	public String share(String appid, String email)
	{
		ShareAppIDEvent event = new ShareAppIDEvent();
		event.operation = ShareAppIDEvent.SHARE;
		event.appid = appid;
		event.email = email;
		
		final StringBuffer result = new StringBuffer();
		EventHandler handler = new EventHandler()
		{
			@Override
			public void onEvent(EventHeader header, Event event)
			{
				if (event instanceof AdminResponseEvent)
				{
					AdminResponseEvent ev = (AdminResponseEvent) event;
					result.append(ev.response != null ? ev.response
					        : ev.errorCause);
				}
				synchronized (this)
				{
					this.notify();
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
		if (result.length() > 0)
		{
			return result.toString();
		}
		return "Failed to share appid!";
		
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
				String[] shareargs = line.getArgs();
				if (shareargs != null && shareargs.length == 2)
				{
					String appid = shareargs[0].trim();
					String email = shareargs[1].trim();
					String response = share(appid, email);
					GAEAdmin.outputln(response);
				}
				else
				{
					GAEAdmin.outputln("Only TWO arg expected!"
					        + Arrays.toString(shareargs));
				}
				
			}
			
		}
		catch (Exception exp)
		{
			exp.printStackTrace();
			System.out.println("Error:" + exp.getMessage());
		}
	}
	
	@Override
	public void printHelp()
	{
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(COMMAND + " <appid> <email>", options);
		
	}
	
}
