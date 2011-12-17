/**
 * 
 */
package org.snova.gae.client.shell.swing;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.snova.framework.shell.swing.GUIPlugin;
import org.snova.gae.client.plugin.GAE;

/**
 * @author wqy
 *
 */
public class GUIPluginWrapper extends GAE implements GUIPlugin
{
	@Override
    public ImageIcon getIcon()
    {
		 return GAEImageUtil.APPENGINE;
    }

	@Override
    public JPanel getConfigPanel()
    {
		if(null == panel)
		{
			panel = new GAEConfigPanel();
		}
	    return panel;
    }
	
	private GAEConfigPanel panel =  null;
}
