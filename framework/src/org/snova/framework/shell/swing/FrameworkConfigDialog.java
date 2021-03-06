/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ConfigJDialog.java
 *
 * Created on 2010-8-14, 18:25:54
 */

package org.snova.framework.shell.swing;

import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;

import org.arch.config.IniProperties;
import org.arch.event.EventDispatcher;
import org.arch.event.NamedEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.spac.SPAC;
import org.snova.framework.util.PreferenceHelper;
import org.snova.http.client.HttpClientHelper;
import org.snova.http.client.common.SimpleSocketAddress;

/**
 * 
 * @author wqy
 */
public class FrameworkConfigDialog extends javax.swing.JDialog
{
	
	public static final String	AUTO_CONNECT	= "GUIAutoConnectProxyServer";
	
	/** Creates new form ConfigJDialog */
	public FrameworkConfigDialog(java.awt.Frame parent, boolean modal)
	{
		super(parent, modal);
		initComponents();
		setIconImage(ImageUtil.CONFIG.getImage());
		setDefaultValueFromConfig();
	}
	
	public void start()
	{
		setDefaultValueFromConfig();
		setVisible(true);
	}
	
	private void setDefaultValueFromConfig()
	{
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		SimpleSocketAddress addr = HttpClientHelper.getHttpRemoteAddress(false,
		        cfg.getProperty("LocalServer", "Listen"));
		localServerHostText.setText(addr.host);
		localServerPortText.setText("" + addr.port);
		tpTextField.setText("20");
		
		List<String> serviceNames = new ArrayList<String>();
		serviceNames.add("GAE");
		serviceNames.add("C4");
		serviceNames.add("SPAC");
		// for (NamedEventHandler handler :
		// EventDispatcher.getSingletonInstance()
		// .getAllNamedEventHandlers())
		// {
		// serviceNames.add(handler.getName());
		// }
		serviceFactoryComboBox.setModel(new DefaultComboBoxModel(serviceNames
		        .toArray()));
		String choice = cfg.getProperty("SPAC", "Default", "Auto");
		if (choice.equals("Auto"))
		{
			choice = "SPAC";
		}
		serviceFactoryComboBox.setSelectedItem(choice);
		String value = PreferenceHelper.getPreference(AUTO_CONNECT);
		if (null != value)
		{
			autoConnectCheckBox.setSelected(value.equals(Boolean.TRUE
			        .toString()));
		}
	}
	
	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
	// <editor-fold defaultstate="collapsed"
	// desc="Generated Code">//GEN-BEGIN:initComponents
	private void initComponents()
	{
		
		applyButton = new javax.swing.JButton();
		jPanel6 = new javax.swing.JPanel();
		jLabel5 = new javax.swing.JLabel();
		localServerHostText = new javax.swing.JTextField();
		jLabel6 = new javax.swing.JLabel();
		localServerPortText = new javax.swing.JTextField();
		jLabel2 = new javax.swing.JLabel();
		tpTextField = new javax.swing.JTextField();
		jPanel1 = new javax.swing.JPanel();
		jLabel1 = new javax.swing.JLabel();
		serviceFactoryComboBox = new javax.swing.JComboBox();
		autoConnectCheckBox = new javax.swing.JCheckBox();
		
		setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
		setTitle("Configuration - snova");
		setAlwaysOnTop(true);
		setLocationByPlatform(true);
		setResizable(false);
		
		applyButton.setText("Apply");
		applyButton.setIcon(ImageUtil.OK);
		applyButton.addActionListener(new java.awt.event.ActionListener()
		{
			public void actionPerformed(java.awt.event.ActionEvent evt)
			{
				applyButtonActionPerformed(evt);
			}
		});
		
		jPanel6.setBorder(javax.swing.BorderFactory
		        .createTitledBorder("Local Server Setting"));
		
		jLabel5.setText("Host:");
		
		localServerHostText.setText("localhost");
		
		jLabel6.setText("Port:");
		
		localServerPortText.setText("48100");
		
		jLabel2.setText("ThreadPoolSize:");
		
		tpTextField.setText("25");
		
		javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(
		        jPanel6);
		jPanel6.setLayout(jPanel6Layout);
		jPanel6Layout
		        .setHorizontalGroup(jPanel6Layout
		                .createParallelGroup(
		                        javax.swing.GroupLayout.Alignment.LEADING)
		                .addGroup(
		                        jPanel6Layout
		                                .createSequentialGroup()
		                                .addContainerGap()
		                                .addGroup(
		                                        jPanel6Layout
		                                                .createParallelGroup(
		                                                        javax.swing.GroupLayout.Alignment.LEADING,
		                                                        false)
		                                                .addGroup(
		                                                        jPanel6Layout
		                                                                .createSequentialGroup()
		                                                                .addComponent(
		                                                                        jLabel5)
		                                                                .addPreferredGap(
		                                                                        javax.swing.LayoutStyle.ComponentPlacement.RELATED)
		                                                                .addComponent(
		                                                                        localServerHostText,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                                        88,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
		                                                                .addGap(18,
		                                                                        18,
		                                                                        18)
		                                                                .addComponent(
		                                                                        jLabel6)
		                                                                .addPreferredGap(
		                                                                        javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
		                                                                .addComponent(
		                                                                        localServerPortText,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
		                                                .addGroup(
		                                                        jPanel6Layout
		                                                                .createSequentialGroup()
		                                                                .addComponent(
		                                                                        jLabel2)
		                                                                .addPreferredGap(
		                                                                        javax.swing.LayoutStyle.ComponentPlacement.RELATED)
		                                                                .addComponent(
		                                                                        tpTextField)))
		                                .addContainerGap(29, Short.MAX_VALUE)));
		jPanel6Layout
		        .setVerticalGroup(jPanel6Layout
		                .createParallelGroup(
		                        javax.swing.GroupLayout.Alignment.LEADING)
		                .addGroup(
		                        jPanel6Layout
		                                .createSequentialGroup()
		                                .addContainerGap()
		                                .addGroup(
		                                        jPanel6Layout
		                                                .createParallelGroup(
		                                                        javax.swing.GroupLayout.Alignment.BASELINE)
		                                                .addComponent(jLabel5)
		                                                .addComponent(
		                                                        localServerHostText,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
		                                                .addComponent(
		                                                        localServerPortText,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
		                                                .addComponent(jLabel6))
		                                .addGap(18, 18, 18)
		                                .addGroup(
		                                        jPanel6Layout
		                                                .createParallelGroup(
		                                                        javax.swing.GroupLayout.Alignment.BASELINE)
		                                                .addComponent(jLabel2)
		                                                .addComponent(
		                                                        tpTextField,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
		                                .addContainerGap(20, Short.MAX_VALUE)));
		
		jPanel1.setBorder(javax.swing.BorderFactory
		        .createTitledBorder("Proxy Service"));
		
		jLabel1.setText("Service Factory:");
		
		autoConnectCheckBox.setText("Auto Connect Proxy Server");
		
		javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(
		        jPanel1);
		jPanel1.setLayout(jPanel1Layout);
		jPanel1Layout
		        .setHorizontalGroup(jPanel1Layout
		                .createParallelGroup(
		                        javax.swing.GroupLayout.Alignment.LEADING)
		                .addGroup(
		                        jPanel1Layout
		                                .createSequentialGroup()
		                                .addContainerGap()
		                                .addGroup(
		                                        jPanel1Layout
		                                                .createParallelGroup(
		                                                        javax.swing.GroupLayout.Alignment.TRAILING,
		                                                        false)
		                                                .addComponent(
		                                                        autoConnectCheckBox,
		                                                        javax.swing.GroupLayout.Alignment.LEADING,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        Short.MAX_VALUE)
		                                                .addGroup(
		                                                        javax.swing.GroupLayout.Alignment.LEADING,
		                                                        jPanel1Layout
		                                                                .createSequentialGroup()
		                                                                .addComponent(
		                                                                        jLabel1)
		                                                                .addPreferredGap(
		                                                                        javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
		                                                                .addComponent(
		                                                                        serviceFactoryComboBox,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                                        110,
		                                                                        javax.swing.GroupLayout.PREFERRED_SIZE)))
		                                .addContainerGap(29, Short.MAX_VALUE)));
		jPanel1Layout
		        .setVerticalGroup(jPanel1Layout
		                .createParallelGroup(
		                        javax.swing.GroupLayout.Alignment.LEADING)
		                .addGroup(
		                        jPanel1Layout
		                                .createSequentialGroup()
		                                .addContainerGap()
		                                .addGroup(
		                                        jPanel1Layout
		                                                .createParallelGroup(
		                                                        javax.swing.GroupLayout.Alignment.BASELINE)
		                                                .addComponent(jLabel1)
		                                                .addComponent(
		                                                        serviceFactoryComboBox,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                        javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                        javax.swing.GroupLayout.PREFERRED_SIZE))
		                                .addGap(18, 18, 18)
		                                .addComponent(autoConnectCheckBox)
		                                .addContainerGap(10, Short.MAX_VALUE)));
		
		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(
		        getContentPane());
		getContentPane().setLayout(layout);
		layout.setHorizontalGroup(layout
		        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
		        .addGroup(
		                layout.createSequentialGroup()
		                        .addGroup(
		                                layout.createParallelGroup(
		                                        javax.swing.GroupLayout.Alignment.TRAILING)
		                                        .addGroup(
		                                                javax.swing.GroupLayout.Alignment.LEADING,
		                                                layout.createSequentialGroup()
		                                                        .addContainerGap()
		                                                        .addComponent(
		                                                                jPanel1,
		                                                                javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                                javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                                Short.MAX_VALUE))
		                                        .addGroup(
		                                                javax.swing.GroupLayout.Alignment.LEADING,
		                                                layout.createSequentialGroup()
		                                                        .addContainerGap()
		                                                        .addComponent(
		                                                                jPanel6,
		                                                                javax.swing.GroupLayout.PREFERRED_SIZE,
		                                                                javax.swing.GroupLayout.DEFAULT_SIZE,
		                                                                javax.swing.GroupLayout.PREFERRED_SIZE))
		                                        .addGroup(
		                                                javax.swing.GroupLayout.Alignment.LEADING,
		                                                layout.createSequentialGroup()
		                                                        .addGap(107,
		                                                                107,
		                                                                107)
		                                                        .addComponent(
		                                                                applyButton)))
		                        .addContainerGap()));
		layout.setVerticalGroup(layout
		        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
		        .addGroup(
		                layout.createSequentialGroup()
		                        .addContainerGap()
		                        .addComponent(jPanel6,
		                                javax.swing.GroupLayout.PREFERRED_SIZE,
		                                javax.swing.GroupLayout.DEFAULT_SIZE,
		                                javax.swing.GroupLayout.PREFERRED_SIZE)
		                        .addPreferredGap(
		                                javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
		                        .addComponent(jPanel1,
		                                javax.swing.GroupLayout.PREFERRED_SIZE,
		                                javax.swing.GroupLayout.DEFAULT_SIZE,
		                                javax.swing.GroupLayout.PREFERRED_SIZE)
		                        .addPreferredGap(
		                                javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
		                        .addComponent(applyButton)
		                        .addContainerGap(18, Short.MAX_VALUE)));
		
		pack();
	}// </editor-fold>//GEN-END:initComponents
	
	private void applyButtonActionPerformed(java.awt.event.ActionEvent evt)
	{// GEN-FIRST:event_applyButtonActionPerformed
		try
		{
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			String listen = localServerHostText.getText() + ":"
			        + localServerPortText.getText().trim();
			cfg.setProperty("LocalServer", "Listen", listen);
			String s = serviceFactoryComboBox.getSelectedItem().toString();
			if (s.equals("SPAC"))
			{
				s = "Auto";
				SPAC.spacEnbale = false;
			}
			else
			{
				SPAC.spacEnbale = true;
			}
			cfg.setProperty("SPAC", "Default", s);
			// cfg.setThreadPoolSize(Integer.parseInt(tpTextField.getText()));
			SnovaConfiguration.getInstance().save();
			PreferenceHelper.savePreference(AUTO_CONNECT,
			        Boolean.valueOf(autoConnectCheckBox.isSelected())
			                .toString());
			setVisible(false);
		}
		catch (Exception ex)
		{
			logger.error("Failed to save config!", ex);
		}
	}// GEN-LAST:event_applyButtonActionPerformed
	
	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String args[])
	{
		java.awt.EventQueue.invokeLater(new Runnable()
		{
			public void run()
			{
				FrameworkConfigDialog dialog = new FrameworkConfigDialog(
				        new javax.swing.JFrame(), true);
				dialog.addWindowListener(new java.awt.event.WindowAdapter()
				{
					public void windowClosing(java.awt.event.WindowEvent e)
					{
						System.exit(0);
					}
				});
				dialog.setVisible(true);
			}
		});
	}
	
	// Variables declaration - do not modify//GEN-BEGIN:variables
	private javax.swing.JButton	   applyButton;
	private javax.swing.JCheckBox	autoConnectCheckBox;
	private javax.swing.JLabel	   jLabel1;
	private javax.swing.JLabel	   jLabel2;
	private javax.swing.JLabel	   jLabel5;
	private javax.swing.JLabel	   jLabel6;
	private javax.swing.JPanel	   jPanel1;
	private javax.swing.JPanel	   jPanel6;
	private javax.swing.JTextField	localServerHostText;
	private javax.swing.JTextField	localServerPortText;
	private javax.swing.JComboBox	serviceFactoryComboBox;
	private javax.swing.JTextField	tpTextField;
	// End of variables declaration//GEN-END:variables
	protected Logger	           logger	= LoggerFactory
	                                              .getLogger(getClass());
}
