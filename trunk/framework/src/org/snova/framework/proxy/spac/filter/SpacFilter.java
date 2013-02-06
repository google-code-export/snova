/**
 * 
 */
package org.snova.framework.proxy.spac.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 * 
 */
public abstract class SpacFilter
{
	private static Map<String, SpacFilter>	filterTable	= new ConcurrentHashMap<String, SpacFilter>();
	
	public abstract boolean filter(HttpRequest req);
	
	public static void init()
	{
		filterTable.put("IsBlockedByGFW", GFWList.getInstacne());
		filterTable.put("IsHostInCN", IsHostCNIP.getInstacne());
	}
	
	public static boolean invokeFilter(String filter, HttpRequest req)
	{
		boolean not = false;
		if (filter.startsWith("!"))
		{
			filter = filter.substring(1);
			not = true;
		}
		SpacFilter func = filterTable.get(filter);
		if (null != func)
		{
			if (not)
			{
				return !func.filter(req);
			}
			return func.filter(req);
		}
		return false;
	}
}
