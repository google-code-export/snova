/**
 * 
 */
package org.snova.heroku.server.servelet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.event.EventRestNotify;
import org.snova.heroku.server.handler.ServerEventHandler;

/**
 * @author wqy
 * 
 */
public class RawEventHeartBeatServlet extends HttpServlet
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	ServerEventHandler handler;

	public RawEventHeartBeatServlet(ServerEventHandler handler)
	{
		this.handler = handler;
	}

	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		try
		{
			handler.getFetchHandler().touch();
			String auth = req.getHeader("X-HerokuAuth");
			//System.out.println("###########Handle auth:" + auth);
			handler.getFetchHandler().handleHerokuAuth(auth);
			
			resp.getOutputStream().println("OK");
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
			// logger.warn("Failed to process message", e);
		}
	}
}
