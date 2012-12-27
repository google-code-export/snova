/**
 * This file is part of the hyk-proxy-core project.
 * Copyright (c) 2011 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ApplicationLauncher.java 
 *
 * @author yinqiwen [ 2011-11-27 | PM09:50:22 ]
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
import org.snova.framework.Snova;
import org.snova.framework.common.Constants;
import org.snova.framework.config.SnovaConfiguration;
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
			String home = SnovaConfiguration.getHome();
			String loggingCfgFile = home + "/conf/logging properties";
			FileInputStream fis = new FileInputStream(loggingCfgFile);
			Properties props = new Properties();
			props.load(fis);
			fis.close();
			PropertiesHelper.replaceSystemProperties(props);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			props.store(bos, "");
			bos.close();
			LogManager.getLogManager().readConfiguration(
			        new ByteArrayInputStream(bos.toByteArray()));
		}
	}

	public static void initLoggerConfig() throws IOException
	{
		System.setProperty("java.util.logging.config.class",
		        JDKLoggingConfig.class.getName());
	}

	public static void main(String[] args) throws IOException
	{
		initLoggerConfig();

		Snova fr = null;
		if (args.length == 0 || args[0].equals("cli"))
		{
			fr = new Snova(new TUITrace());
			fr.start();
		}
		else if (args[0].equals("gui"))
		{
			MainFrame.main(null);
		}
	}
}
