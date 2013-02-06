/**
 * 
 */
package org.snova.framework.proxy.spac.filter.gfwlist;

import org.arch.util.StringHelper;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.snova.framework.util.MiscHelper;

/**
 * @author yinqiwen
 * 
 */
public class HostUrlWildcardRule implements GFWListRule
{
	public boolean	onlyHttp;
	public String	hostRule;
	public String	urlRule;
	private String origin;
	
	@Override
	public boolean init(String rule)
	{
		origin = rule;
		if (!rule.contains("/"))
		{
			hostRule = rule;
			return true;
		}
		String[] rules = rule.split("/", 2);
		hostRule = rules[0];
		if (rules.length == 2)
		{
			urlRule = rules[1];
		}
		return true;
	}
	
	@Override
	public boolean match(HttpRequest req)
	{
		if (onlyHttp && req.getMethod().equals(HttpMethod.CONNECT))
		{
			return false;
		}
		if (hostRule != null
		        && !StringHelper.wildCardMatch(HttpHeaders.getHost(req),
		                hostRule))
		{
			return false;
		}
		if (null != urlRule)
		{
			return StringHelper.wildCardMatch(
			        MiscHelper.getURLString(req, true), urlRule);
		}
		//System.out.println("###WildcardRule for " + HttpHeaders.getHost(req) +  "##" + origin);
		return true;
	}
}
