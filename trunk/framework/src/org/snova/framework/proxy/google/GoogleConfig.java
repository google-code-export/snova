/**
 * 
 */
package org.snova.framework.proxy.google;

import java.util.ArrayList;
import java.util.List;

import org.arch.config.IniProperties;
import org.arch.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.ssh.SSH;

/**
 * @author wqy
 * 
 */
public class GoogleConfig
{
	protected static Logger	logger	= LoggerFactory
	                                       .getLogger(GoogleConfig.class);
	
	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		if (cfg.getIntProperty("Google", "Enable", 1) == 0)
		{
			return false;
		}
		return true;
	}
}
