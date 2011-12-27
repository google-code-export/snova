/**
 * 
 */
package org.snova.heroku.server.servelet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.snova.heroku.server.handler.EventSendService;

/**
 * @author wqy
 *
 */
public class IndexServlet extends HttpServlet 
{
	@Override
	protected void doGet(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		resp.getOutputStream().print("Welcom to snova-heroku server!");
	}
}
