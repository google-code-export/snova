/**
 * 
 */
package org.arch.misc.gfw;

import java.util.regex.Pattern;

/**
 * @author wqy
 * 
 */
class GFWListRule
{
	static enum GFWListRuleType
	{
		HTTP_STR, HOST_MATCH, REGEX_MATCH, URL_START_MATCH,
	}
	
	GFWListRuleType	type;
	String	        rule;
	
	public GFWListRule(GFWListRuleType type, String rule)
    {
	    this.type = type;
	    this.rule = rule;
    }

	private Pattern	pattern;
	
	private boolean httpStrMatch(String url)
	{
		return url.startsWith("http://") && url.indexOf(rule) != -1;
	}
	
	private boolean hostMatch(String url)
	{
		String host = url;
		if (host.startsWith("http://"))
		{
			host = host.substring("http://".length());
		}
		else if (host.startsWith("https://"))
		{
			host = host.substring("https://".length());
		}
		int index = host.indexOf("/");
		if (index > 0)
		{
			host = host.substring(0, index);
		}
		return host.indexOf(rule) != -1;
	}
	
	private boolean urlStartMatch(String url)
	{
		return url.startsWith(rule);
	}
	
	private boolean regexMatch(String url)
	{
		if (null == pattern)
		{
			pattern = Pattern.compile(rule);
		}
		return pattern.matcher(url).matches();
	}
	
	public boolean isGFWBlocked(String url)
	{
		switch (type)
		{
			case HTTP_STR:
			{
				return httpStrMatch(url);
			}
			case HOST_MATCH:
			{
				return hostMatch(url);
			}
			case URL_START_MATCH:
			{
				return urlStartMatch(url);
			}
			case REGEX_MATCH:
			{
				return regexMatch(url);
			}
			default:
			{
				return false;
			}
		}
	}
}
