/**
 * 
 */
package org.snova.framework.config;

import java.io.File;

/**
 * @author wqy
 *
 */
public interface ReloadableConfiguration
{
	public void reload();
	public File getConfigurationFile();
}
