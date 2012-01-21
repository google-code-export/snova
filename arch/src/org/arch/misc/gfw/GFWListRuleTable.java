/**
 * 
 */
package org.arch.misc.gfw;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.arch.misc.gfw.GFWListRule.GFWListRuleType;

/**
 * @author wqy
 * 
 */
class GFWListRuleTable
{
	private List<GFWListRule>	excludeList	= new LinkedList<GFWListRule>();
	private List<GFWListRule>	matchList	= new LinkedList<GFWListRule>();
	
	boolean isBlockedByGFW(String url)
	{
		for (GFWListRule rule : excludeList)
		{
			if (rule.isGFWBlocked(url))
			{
				return false;
			}
		}
		for (GFWListRule rule : matchList)
		{
			if (rule.isGFWBlocked(url))
			{
				System.out.println(rule.type + rule.rule);
				return true;
			}
		}
		return false;
	}
	
	void addExcludeSet(String rule)
	{
		excludeList.add(new GFWListRule(GFWListRuleType.HTTP_STR, rule));
	}
	
	void addHostMatch(String rule)
	{
		matchList.add(new GFWListRule(GFWListRuleType.HOST_MATCH, rule));
	}
	
	void addRegexMatch(String rule)
	{
		matchList.add(new GFWListRule(GFWListRuleType.REGEX_MATCH, rule));
	}
	
	void addURLStartMatch(String rule)
	{
		matchList.add(new GFWListRule(GFWListRuleType.URL_START_MATCH, rule));
	}
	
	void addHTTPStrMatch(String rule)
	{
		matchList.add(new GFWListRule(GFWListRuleType.HTTP_STR, rule));
	}
}
