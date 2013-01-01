/**
 * 
 */
package org.snova.framework.proxy.c4;

import java.util.ArrayList;
import java.util.List;

import org.arch.config.IniProperties;
import org.arch.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4;

/**
 * @author wqy
 * 
 */
public class C4Config
{
	protected static Logger	  logger	= LoggerFactory
	                                           .getLogger(C4Config.class);
	static List<C4ServerAuth>	appids	= new ArrayList<C4ServerAuth>();
	
	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		if (cfg.getIntProperty("C4", "Enable", 1) == 0)
		{
			return false;
		}
		for (int i = 0;; i++)
		{
			String v = cfg.getProperty("C4", "WorkerNode[" + i + "]");
			if (null == v || v.length() == 0)
			{
				break;
			}
			if (!v.endsWith("/"))
			{
				v = v + "/";
			}
			if (v.indexOf("://") == -1)
			{
				v = "http://" + v;
			}
			C4ServerAuth auth = new C4ServerAuth();
			if (!auth.parse(v.trim()))
			{
				logger.error("Failed to parse appid:" + v);
				break;
			}
			appids.add(auth);
		}
		return true;
	}
}
