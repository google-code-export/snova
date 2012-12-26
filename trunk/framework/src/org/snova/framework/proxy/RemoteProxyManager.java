/**
 * 
 */
package org.snova.framework.proxy;

/**
 * @author qiyingwang
 * 
 */
public interface RemoteProxyManager
{
	public String getName();

	public RemoteProxyHandler createProxyHandler();
}
