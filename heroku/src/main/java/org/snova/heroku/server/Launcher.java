/**
 * 
 */
package org.snova.heroku.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.snova.heroku.common.event.HerokuEvents;
import org.snova.heroku.server.handler.ServerEventHandler;
import org.snova.heroku.server.servelet.HTTPEventDispatcherServlet;
import org.snova.heroku.server.servelet.IndexServlet;
import org.snova.heroku.server.servelet.RawEventHeartBeatServlet;


/**
 * @author wqy
 *
 */
public class Launcher
{
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		ServerEventHandler handler = new ServerEventHandler();
		HerokuEvents.init(handler, true);
		String portstr = System.getenv("PORT");
		Server server = new Server(Integer.valueOf(null == portstr ? "8080"
		        : portstr));
		ServletContextHandler context = new ServletContextHandler(
		        ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new IndexServlet()), "/*");
		context.addServlet(new ServletHolder(new HTTPEventDispatcherServlet(
		        handler)), "/invoke");
		context.addServlet(new ServletHolder(new RawEventHeartBeatServlet(
		        handler)), "/raw");
		server.start();
		server.join();
	}
	
}
