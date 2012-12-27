/**
 * 
 */
package org.snova.framework.shell.swing;

import javax.swing.Icon;
import javax.swing.JPanel;

/**
 * @author qiyingwang
 * 
 */
public interface ProxyGUIHolder
{
	public String getDesc();

	public String getName();

	Icon getIcon();

	JPanel getConfigPanel();
}
