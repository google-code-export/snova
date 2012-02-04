/**
 * This file is part of the hyk-proxy-framework project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: SwingHelper.java 
 *
 * @author yinqiwen [ 2010-8-29 |07:04:15 PM]
 *
 */
package org.snova.framework.shell.swing;

import java.awt.Desktop;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.Future;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.arch.util.ListSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class SwingHelper
{
	protected static Logger	logger	= LoggerFactory
	                                       .getLogger(SwingHelper.class);
	
	public static void browseWebpage(String site)
	{
		try
		{
			Desktop desktop = Desktop.getDesktop();
			desktop.browse(new URI(site));
		}
		catch (Exception e)
		{
			logger.error("Failed to go to web site!", e);
		}
		
	}
	
	public static void showBusyButton(Future runninttask, JButton button,
	        String busytext)
	{
		ListSelector<ImageIcon> busys = new ListSelector<ImageIcon>(
		        Arrays.asList(ImageUtil.BUSY_ICONS), false);
		while (true)
		{
			if (runninttask.isDone())
			{
				break;
			}
			button.setIcon(busys.select());
			button.setText(busytext);
			try
			{
				Thread.sleep(200);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
	
}
