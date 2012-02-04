/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.snova.c4.common.C4PluginVersion;

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
		resp.getOutputStream().print("Welcom to snova-c4 server v" + C4PluginVersion.value + "!");
	}
}
