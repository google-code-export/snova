/**
 * 
 */
package org.snova.framework.proxy.spac.filter.gfwlist;

import org.arch.util.StringHelper;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.snova.framework.util.MiscHelper;

/**
 * @author yinqiwen
 * 
 */
public class UrlWildcardRule implements GFWListRule
{
	public String	urlRule;
	
	@Override
	public boolean init(String rule)
	{
		if (!rule.contains("*"))
		{
			rule = "*" + rule;
		}
		urlRule = rule;
		return true;
	}
	
	@Override
	public boolean match(HttpRequest req)
	{
		return StringHelper.wildCardMatch(MiscHelper.getURLString(req, false),
		        urlRule);
	}
}
