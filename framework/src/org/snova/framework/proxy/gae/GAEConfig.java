/**
 * 
 */
package org.snova.framework.proxy.gae;

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
public class GAEConfig
{
	protected static Logger logger = LoggerFactory.getLogger(GAEConfig.class);
	static List<GAEServerAuth> appids = new ArrayList<GAEServerAuth>();
	static String masterAppID = "snova-master";
	static String[] injectRange = null;

	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		if (cfg.getIntProperty("GAE", "Enable", 1) == 0)
		{
			return false;
		}
		for (int i = 0;; i++)
		{
			String v = cfg.getProperty("GAE", "WorkerNode[" + i + "]");
			if (null == v || v.length() == 0)
			{
				break;
			}
			GAEServerAuth auth = new GAEServerAuth();
			if (!auth.parse(v.trim()))
			{
				logger.error("Failed to parse appid:" + v);
				break;
			}
			appids.add(auth);
		}
		if (appids.isEmpty() && (C4.enable || SSH.enable))
		{
			return false;
		}
		masterAppID = cfg.getProperty("GAE", "MasterAppID", "snova-master");
		String tmp = cfg.getProperty("GAE", "InjectRange");
		if (!StringHelper.isEmptyString(tmp))
		{
			injectRange = tmp.split("[,|;|\\|]");
		}
		return true;
	}
}
