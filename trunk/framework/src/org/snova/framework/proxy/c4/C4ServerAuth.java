/**
 * 
 */
package org.snova.framework.proxy.c4;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * @author wqy
 * 
 */
public class C4ServerAuth
{
	public URI url;

	public boolean parse(String line)
	{
		try
		{

			url = new URI(line);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public String toString()
	{
		return url.toString();
	}
}
