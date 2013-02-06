/**
 * 
 */
package org.snova.framework.proxy.spac.filter.gfwlist;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * @author yinqiwen
 * 
 */
public interface GFWListRule
{
	public boolean init(String rule);
	
	public boolean match(HttpRequest req);
}
