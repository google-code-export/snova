package org.snova.c4.launch;
/**
 * 
 */


import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.snova.c4.server.servlet.HTTPEventDispatcherServlet;
import org.snova.c4.server.servlet.IndexServlet;



/**
 * @author wqy
 *
 */
public class LauncherServer
{
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		String portstr = System.getenv("PORT");
		Server server = new Server(Integer.valueOf(null == portstr ? "8080"
		        : portstr));
		ServletContextHandler context = new ServletContextHandler(
		        ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new IndexServlet()), "/*");
		context.addServlet(new ServletHolder(new HTTPEventDispatcherServlet()), "/invoke");

		server.start();
		server.join();
	}
	
}
