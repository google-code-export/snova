/**
 * 
 */
package org.snova.c4.server.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * @author wqy
 *
 */
public class ServletHelper
{
	private static final int DEAULT_MAX_RES_SIZE= 1024*1024;
	private static final int DEAULT_TRANASCTION_TIME= 25000;
	public static int getMaxResponseSize(HttpServletRequest req)
	{
		String size = req.getHeader("MaxResponseSize");
		if(null == size)
		{
			return DEAULT_MAX_RES_SIZE;
		}
		try
        {
			int v = Integer.parseInt(size);
			if(v < 4096)
			{
				return DEAULT_MAX_RES_SIZE;
			}
			return v;
        }
        catch (Exception e)
        {
	        // TODO: handle exception
        }
		
		return DEAULT_MAX_RES_SIZE;
	}
	
	public static int getTransacTime(HttpServletRequest req)
	{
		String size = req.getHeader("TransactionTime");
		if(null == size)
		{
			return DEAULT_TRANASCTION_TIME;
		}
		try
        {
			int v = Integer.parseInt(size);
			if(v < 5000)
			{
				return DEAULT_TRANASCTION_TIME;
			}
			return v;
        }
        catch (Exception e)
        {
	        // TODO: handle exception
        }
		
		return DEAULT_TRANASCTION_TIME;
	}
}
