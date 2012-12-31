/**
 * 
 */
package org.snova.framework.proxy.spac;

import java.io.File;
import java.util.ArrayList;

import org.arch.config.IniProperties;
import org.arch.util.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4;
import org.snova.framework.proxy.gae.GAE;
import org.snova.framework.proxy.ssh.SSH;

/**
 * @author wqy
 * 
 */
public class SpacConfig
{
	protected static Logger logger = LoggerFactory.getLogger(SpacConfig.class);

	private static String defaultProxy = "GAE";
	static SpacRule[] spacRules = new SpacRule[0];

	private static void parseSpacRules(JSONArray array,
	        ArrayList<SpacRule> rules) throws JSONException
	{

		int len = array.length();
		for (int i = 0; i < len; i++)
		{
			JSONObject obj = array.getJSONObject(i);
			rules.add(new SpacRule(obj));
		}
	}

	private static boolean loadSpacRules()
	{
		try
		{
			String cloudSpacContent = FileHelper.readEntireFile(new File(
			        SnovaConfiguration.getHome() + "/spac", "cloud_spac.json"));
			String userPostSpacContent = FileHelper.readEntireFile(new File(
			        SnovaConfiguration.getHome() + "/spac", "user_spac.json"));

			ArrayList<SpacRule> rules = new ArrayList<SpacRule>();
			File usePreRule = new File(SnovaConfiguration.getHome() + "/spac",
			        "user_pre_spac.json");
			if (usePreRule.exists())
			{
				String userPreSpacContent = FileHelper
				        .readEntireFile(usePreRule);
				JSONArray userPreSpac = new JSONArray(userPreSpacContent);
				parseSpacRules(userPreSpac, rules);
			}
			JSONArray cloudSpac = new JSONArray(cloudSpacContent);
			JSONArray userPostSpac = new JSONArray(userPostSpacContent);
			parseSpacRules(cloudSpac, rules);
			parseSpacRules(userPostSpac, rules);
			spacRules = new SpacRule[rules.size()];
			rules.toArray(spacRules);
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to init spac rules.", e);
			return false;
		}
	}

	public static boolean init()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		defaultProxy = cfg.getProperty("SPAC", "Default", "GAE");
		if (defaultProxy.equalsIgnoreCase("Auto"))
		{
			if (GAE.enable)
			{
				defaultProxy = "GAE";
			}
			else if (C4.enable)
			{
				defaultProxy = "C4";
			}
			else if (SSH.enable)
			{
				defaultProxy = "SSH";
			}
			else
			{
				defaultProxy = "Direct";
			}
		}
		if (cfg.getIntProperty("SPAC", "Enable", 1) == 0)
		{
			return false;
		}
		if (!loadSpacRules())
		{
			return false;
		}
		return true;
	}
}
