/**
 * 
 */
package org.snova.framework.proxy;

import io.netty.handler.codec.http.HttpRequest;

/**
 * @author qiyingwang
 * 
 */
public interface RemoteProxyManager
{
	public String getName();

	public RemoteProxyHandler createProxyHandler();
}
