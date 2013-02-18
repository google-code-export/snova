/**
 * 
 */
package org.snova.framework.proxy;

import java.util.Map;

/**
 * @author yinqiwen
 * 
 */
public interface RemoteProxyManager
{
	public String getName();
	
	public RemoteProxyHandler createProxyHandler(Map<String, String> attr);
}
