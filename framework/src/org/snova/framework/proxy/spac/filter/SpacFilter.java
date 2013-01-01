/**
 * 
 */
package org.snova.framework.proxy.spac.filter;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 * 
 */
public interface SpacFilter
{
	public boolean filter(HttpRequest req);
}
