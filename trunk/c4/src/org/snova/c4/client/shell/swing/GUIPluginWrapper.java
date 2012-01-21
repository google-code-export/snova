/**
 * 
 */
package org.snova.c4.client.shell.swing;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

import org.snova.c4.client.plugin.C4;
import org.snova.framework.shell.swing.GUIPlugin;

/**
 * @author wqy
 *
 */
public class GUIPluginWrapper extends C4 implements GUIPlugin
{

	@Override
    public ImageIcon getIcon()
    {
	    // TODO Auto-generated method stub
	    return new javax.swing.ImageIcon(
	    		GUIPluginWrapper.class.getResource("/images/c4.png"));
    }

	@Override
    public JPanel getConfigPanel()
    {
	    // TODO Auto-generated method stub
	    return null;
    }
	
}
