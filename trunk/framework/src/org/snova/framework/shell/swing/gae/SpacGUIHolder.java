/**
 * 
 */
package org.snova.framework.shell.swing.gae;

import javax.swing.Icon;
import javax.swing.JPanel;

import org.snova.framework.shell.swing.ImageUtil;
import org.snova.framework.shell.swing.ProxyGUIHolder;

/**
 * @author qiyingwang
 *
 */
public class SpacGUIHolder implements ProxyGUIHolder
{

	@Override
    public String getDesc()
    {
	    return "SPAC plugin.";
    }

	@Override
    public Icon getIcon()
    {
	    return null;
    }

	@Override
    public String getName()
    {
	    return "SPAC";
    }

	@Override
    public JPanel getConfigPanel()
    {
	    // TODO Auto-generated method stub
	    return null;
    }

}
