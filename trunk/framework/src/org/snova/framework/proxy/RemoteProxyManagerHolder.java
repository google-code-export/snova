/**
 * 
 */
package org.snova.framework.proxy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yinqiwen
 * 
 */
public class RemoteProxyManagerHolder
{
	private static Map<String, RemoteProxyManager>	table	= new ConcurrentHashMap<String, RemoteProxyManager>();
	
	public static void registerRemoteProxyManager(RemoteProxyManager manager)
	{
		table.put(manager.getName(), manager);
	}
	
	public static void registerRemoteProxyManager(String name,
	        RemoteProxyManager manager)
	{
		table.put(name, manager);
	}
	
	public static RemoteProxyManager getRemoteProxyManager(String name)
	{
		return table.get(name);
	}
}
