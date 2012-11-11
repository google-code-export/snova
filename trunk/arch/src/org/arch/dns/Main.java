/**
 * This file is part of the Test project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Main.java 
 *
 * @author yinqiwen [ 2010-8-29 | ÏÂÎç05:02:48 ]
 *
 */
package org.arch.dns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

import javax.naming.NamingException;

/**
 *
 */
public class Main
{
	
	/**
	 * @param args
	 * @throws IOException
	 * @throws NamingException
	 */
	public static void main(String[] args) throws IOException, NamingException
	{
		ResolveOptions options = new ResolveOptions();
		options.useTcp = true;
		String[] ips = Resolver.resolveIPv4(new String[] { "8.8.8.8" },
		        "facebook.com", options);
		System.out.println(Arrays.toString(ips));
	}
	
}
