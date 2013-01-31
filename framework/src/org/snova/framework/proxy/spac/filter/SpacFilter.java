/**
 * 
 */
package org.snova.framework.proxy.spac.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 * 
 */
public abstract class SpacFilter
{
	private static Map<String, SpacFilter> filterTable = new ConcurrentHashMap<String, SpacFilter>();
	public abstract boolean filter(HttpRequest req);
	
	public static void init()
	{
		filterTable.put("IsBlockedByGFW", GFWList.getInstacne());
		filterTable.put("IsHostInCN", IsHostCNIP.getInstacne());
	}
}
