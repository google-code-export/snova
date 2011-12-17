/**
 * 
 */
package org.snova.framework.plugin;

/**
 * @author qiyingwang
 *
 */
public interface PluginManager
{
	public void loadPlugins();
	public void activatePlugins();
	public void startPlugins();
	public void stopPlugins();
}
