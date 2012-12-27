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
public class GAEGUIHolder implements ProxyGUIHolder
{

	@Override
    public String getDesc()
    {
	    return "Default plugin, powered by Google AppEngine.";
    }

	@Override
    public Icon getIcon()
    {
	    return ImageUtil.APPENGINE;
    }

	@Override
    public String getName()
    {
	    return "GAE";
    }

	@Override
    public JPanel getConfigPanel()
    {
	    // TODO Auto-generated method stub
	    return null;
    }

}
