/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.snova.c4.server.session.v3.RemoteProxySessionManager;

/**
 * @author wqy
 * 
 */
public class PushServlet extends HttpServlet
{
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		String userToken = req.getHeader("UserToken");
		if (null == userToken)
		{
			userToken = "";
		}
		String miscInfo = req.getHeader("C4MiscInfo");
		String[] misc = miscInfo.split("_");
		int index = Integer.parseInt(misc[0]);
		int bodylen = req.getContentLength();
		if (bodylen > 0)
		{
			Buffer content = new Buffer(bodylen);
			int len = 0;
			while (len < bodylen)
			{
				content.read(req.getInputStream());
				len = content.readableBytes();
			}
			if (len > 0)
			{
				try
				{
					RemoteProxySessionManager.getInstance().dispatchEvent(
					        userToken, index, content);
				}
				catch (Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		resp.setContentLength(0);
		resp.setStatus(200);
	}
}