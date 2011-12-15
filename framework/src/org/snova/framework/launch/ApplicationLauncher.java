/**
 * This file is part of the hyk-proxy-core project.
 * Copyright (c) 2011 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ApplicationLauncher.java 
 *
 * @author yinqiwen [ 2011-11-27 | ÏÂÎç09:50:22 ]
 *
 */
package org.snova.framework.launch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.LogManager;

import org.arch.util.PropertiesHelper;
import org.snova.framework.Framework;
import org.snova.framework.common.Constants;
import org.snova.framework.config.DesktopFrameworkConfiguration;
import org.snova.framework.plugin.DesktopPluginManager;
import org.snova.framework.shell.swing.MainFrame;
import org.snova.framework.trace.TUITrace;

/**
 *
 */
public class ApplicationLauncher
{
	public static class JDKLoggingConfig
	{
		public JDKLoggingConfig() throws Exception
		{
			String home = System.getProperty(Constants.APP_HOME);
			if (null == home)
			{
				home = ".";
			}
			String loggingCfgFile = home + "/conf/logging properties";
			FileInputStream fis = new FileInputStream(loggingCfgFile);
			Properties props = new Properties();
			props.load(fis);
			fis.close();
			PropertiesHelper.replaceSystemProperties(props);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			props.store(bos, "");
			bos.close();
			LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(bos.toByteArray()));
		}
	}
	
	public static void initLoggerConfig() throws IOException
	{
		System.setProperty("java.util.logging.config.class", JDKLoggingConfig.class.getName());
		//System.setProperty("java.util.logging.config.file", loggingCfgFile);
	}

	public static void main(String[] args) throws IOException
	{
		initLoggerConfig();

		Framework fr = null;
		if (args.length == 0 || args[0].equals("cli"))
		{
			fr = new Framework(DesktopFrameworkConfiguration.getInstance(),
			        DesktopPluginManager.getInstance(), new TUITrace());
			fr.start();
		}
		else if(args[0].equals("gui"))
		{
			MainFrame.main(null);
		}
		
	}
}
