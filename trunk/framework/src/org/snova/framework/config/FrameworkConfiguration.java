/**
 * 
 */
package org.snova.framework.config;


/**
 * @author qiyingwang
 *
 */
public interface FrameworkConfiguration
{
	public String getProxyEventHandler();

	public SimpleSocketAddress getLocalProxyServerAddress();

	public int getThreadPoolSize();
}
