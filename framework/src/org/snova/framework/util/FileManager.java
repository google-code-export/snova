/**
 * 
 */
package org.snova.framework.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.arch.util.FileHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;

/**
 * @author yinqiwen
 * 
 */
public class FileManager
{
	protected static Logger	logger	= LoggerFactory
	                                       .getLogger(FileManager.class);
	
	public static void writeFile(String content, String file)
	{
		writeFile(content.getBytes(), file);
	}
	
	public static void writeFile(byte[] content, String file)
	{
		String home = SnovaConfiguration.getHome();
		file = home + "/" + file;
		try
		{
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(content);
		}
		catch (Exception e)
		{
			logger.error("Failed to write file", e);
		}
	}
	
	public static String loadFile(String file)
	{
		String home = SnovaConfiguration.getHome();
		file = home + "/" + file;
		try
		{
			return FileHelper.readEntireFile(new File(file));
		}
		catch (IOException e)
		{
			logger.error("Failed to read file", e);
		}
		return null;
	}
	
}
