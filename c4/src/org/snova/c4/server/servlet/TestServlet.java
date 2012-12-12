/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.snova.c4.common.C4PluginVersion;

/**
 * @author wqy
 *
 */
public class TestServlet extends HttpServlet 
{
	
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		resp.setStatus(200);
		resp.getOutputStream().close();
	}
}
