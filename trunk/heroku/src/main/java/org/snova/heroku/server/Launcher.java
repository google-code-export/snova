/**
 * 
 */
package org.snova.heroku.server;

import org.arch.event.EventDispatcher;
import org.arch.event.EventHandler;
import org.arch.event.EventSegment;
import org.arch.event.http.HTTPErrorEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressorType;
import org.snova.heroku.common.event.HerokuEvents;

import org.snova.heroku.server.handler.ServerEventHandler;
import org.snova.heroku.server.servelet.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.*;


/**
 * @author wqy
 *
 */
public class Launcher
{
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception{
		ServerEventHandler handler = new ServerEventHandler();
		HerokuEvents.init(handler, true);
		String portstr = System.getenv("PORT");
        Server server = new Server(Integer.valueOf(null == portstr?"8080":portstr));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new IndexServlet()),"/*");
        context.addServlet(new ServletHolder(new HTTPEventDispatcherServlet(handler)),"/invoke");
        
        server.start();
        server.join();   
    }
	
}
