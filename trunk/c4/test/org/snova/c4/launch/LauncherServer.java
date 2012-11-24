package org.snova.c4.launch;
/**
 * 
 */


import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.snova.c4.server.servlet.InvokeServlet;
import org.snova.c4.server.servlet.IndexServlet;
import org.snova.c4.server.servlet.PushPullServlet;
import org.snova.c4.server.servlet.RSocketHeartBeatServlet;
import org.snova.httpdns.DNSServlet;



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
//		Server server = new Server(Integer.valueOf(null == portstr ? "8080"
//		        : portstr));
		Server server = new Server();
		
		ServletContextHandler context = new ServletContextHandler(
		        ServletContextHandler.NO_SESSIONS | ServletContextHandler.NO_SECURITY);
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new IndexServlet()), "/*");
		context.addServlet(new ServletHolder(new PushPullServlet()), "/invoke2");
		context.addServlet(new ServletHolder(new DNSServlet()), "/dns");
		QueuedThreadPool pool = new QueuedThreadPool(30);
		pool.setMaxIdleTimeMs(30000);
		//server.setConnectors(arg0)
		server.setThreadPool(pool);
		SelectChannelConnector connector = new SelectChannelConnector();
		connector.setThreadPool(pool);
		connector.setAcceptors(2);
		connector.setMaxIdleTime(1000 * 120);
        connector.setSoLingerTime(-1);
        connector.setAcceptQueueSize(10);
        connector.setReuseAddress(true);
        connector.setUseDirectBuffers(true);
		connector.setPort(Integer.valueOf(null == portstr ? "8080"
		        : portstr));
		server.setConnectors(new Connector[]{connector});
		server.start();
		server.join();
	}
	
}
