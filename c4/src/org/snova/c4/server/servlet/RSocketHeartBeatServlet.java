/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.server.service.RSocketService;

/**
 * @author qiyingwang
 *
 */
public class RSocketHeartBeatServlet extends HttpServlet
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
	        throws ServletException, IOException
	{
		String auth = req.getHeader("Host");
		String remote = req.getHeader(C4Constants.LOCAL_RSERVER_ADDR_HEADER);
		if(null != remote)
		{
			RSocketService.routine(auth, remote, req.getHeader(C4Constants.USER_TOKEN_HEADER));
		}
		resp.setStatus(200);
		if(null == remote)
		{
			resp.getOutputStream().println("#####Debug request received.");
		}
	}
}
