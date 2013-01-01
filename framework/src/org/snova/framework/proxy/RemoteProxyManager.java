/**
 * 
 */
package org.snova.framework.proxy;

/**
 * @author yinqiwen
 * 
 */
public interface RemoteProxyManager
{
	public String getName();

	public RemoteProxyHandler createProxyHandler(String[] attr);
}
