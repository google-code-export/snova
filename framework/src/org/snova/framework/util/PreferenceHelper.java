/**
 * 
 */
package org.snova.framework.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.Properties;

import org.snova.framework.config.SnovaConfiguration;


/**
 * @author qiyingwang
 * 
 */
public class PreferenceHelper
{
	private static Properties prefProps = new Properties();

	static
	{
		File pref = new File(SnovaConfiguration.getHome(), ".pref");
		if(null != pref)
		{
			try
            {
	            FileInputStream fis = new FileInputStream(pref);
	            prefProps.load(fis);
	            fis.close();
            }
            catch (Exception e)
            {
	            //e.printStackTrace();
            }
			
		}
	}

	public static void savePreference(String key, String value)
	{
		prefProps.setProperty(key, value);
		File pref = new File(SnovaConfiguration.getHome(), ".pref");
		if (null != pref)
		{
			try
            {
	            FileOutputStream fos = new FileOutputStream(pref);
	            prefProps.store(fos, "Modified at " + new Date());
	            fos.close();
            }
            catch (Exception e)
            {
	            
            }
		}
	}

	public static String getPreference(String key)
	{
		return prefProps.getProperty(key);
	}
}
