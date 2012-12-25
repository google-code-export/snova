/**
 * 
 */
package org.snova.framework.util;

import java.io.File;

/**
 * @author wqy
 *
 */
public interface ReloadableFileMonitor
{
	public void reload();
	public File getMonitorFile();
}
