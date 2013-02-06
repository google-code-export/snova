/**
 * 
 */
package org.snova.framework.proxy.spac.filter.gfwlist;

import java.util.regex.Pattern;

import org.arch.util.StringHelper;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.snova.framework.util.MiscHelper;

/**
 * @author yinqiwen
 * 
 */
public class UrlRegexRule implements GFWListRule
{
	public boolean	is_raw_regex;
	public Pattern	urlReg;
	
	@Override
	public boolean init(String rule)
	{
		if (is_raw_regex)
		{
			urlReg = Pattern.compile(rule);
		}
		else
		{
			urlReg = StringHelper.prepareRegexPattern(rule);
		}
		return true;
	}
	
	@Override
	public boolean match(HttpRequest req)
	{
		return urlReg.matcher(MiscHelper.getURLString(req, false)).matches();
	}
	
}
