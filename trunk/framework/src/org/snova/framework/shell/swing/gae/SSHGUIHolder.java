/**
 * 
 */
package org.snova.framework.shell.swing.gae;

import javax.swing.Icon;
import javax.swing.JPanel;

import org.snova.framework.shell.swing.ProxyGUIHolder;

/**
 * @author qiyingwang
 * 
 */
public class SSHGUIHolder implements ProxyGUIHolder
{

	@Override
	public String getDesc()
	{
		return "SSH plugin, powered by JSch.";
	}

	@Override
	public Icon getIcon()
	{
		return null;
	}

	@Override
	public String getName()
	{
		return "SSH";
	}

	@Override
	public JPanel getConfigPanel()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
