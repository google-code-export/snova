/**
 * 
 */
package org.snova.framework.proxy.c4;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author wqy
 * 
 */
public class C4ServerAuth
{
	public URL url;

	public boolean parse(String line)
	{
		try
		{
			url = new URL(line);
		}
		catch (MalformedURLException e)
		{
			return false;
		}
		return true;
	}

	public String toString()
	{
		return url.toString();
	}
}
