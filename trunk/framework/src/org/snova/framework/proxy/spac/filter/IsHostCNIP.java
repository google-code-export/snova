/**
 * 
 */
package org.snova.framework.proxy.spac.filter;

import org.jboss.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 *
 */
public  class IsHostCNIP extends SpacFilter
{
    private static IsHostCNIP instance = new IsHostCNIP();
	private IsHostCNIP() {
	}

	static IsHostCNIP getInstacne() {
		return instance;
	}
    public boolean filter(HttpRequest req)
    {
	    return false;
    }

}
