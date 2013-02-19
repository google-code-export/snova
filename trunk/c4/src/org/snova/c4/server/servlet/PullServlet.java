/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.snova.c4.server.io.Puller;

/**
 * @author yinqiwen
 * 
 */
public class PullServlet extends HttpServlet
{
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		Puller.execute(req, resp);
	}
}