/**
 * 
 */
package org.snova.framework.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URL;

import org.arch.util.NetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author qiyingwang
 * 
 */
public class GeneralNetworkHelper
{
	protected static Logger logger = LoggerFactory
	        .getLogger(GeneralNetworkHelper.class);

	public static String getPublicIP()
	{
		return getPublicIP(null);
	}
	public static String getPublicIP(Proxy p)
	{
		try
		{
			InetAddress addr = InetAddress.getLocalHost();
			if(!NetworkHelper.isPrivateIP(addr.getHostAddress()))
			{
				return addr.getHostAddress();
			}
			URL getUrl = new URL("http://icanhazip.com");
			HttpURLConnection connection = null;
			if(null == p)
			{
				connection = (HttpURLConnection) getUrl
				        .openConnection();
			}
			else
			{
				connection = (HttpURLConnection) getUrl
				        .openConnection(p);
			}
			// connection.

			connection.connect();

			BufferedReader reader = new BufferedReader(new InputStreamReader(
			        connection.getInputStream()));
			String lines = reader.readLine();
			//System.out.println(lines);
			reader.close();

			connection.disconnect();
			return lines.trim();
		}
		catch (MalformedURLException e)
		{
			logger.error("Failed", e);
			return null;
		}
		catch (IOException e)
		{
			logger.error("Failed", e);
			return null;
		}
	}

	public static void main(String[] args)
	{
		getPublicIP();
	}
}
