/**
 * 
 */
package org.snova.framework.common;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * @author qiyingwang
 * 
 */
public interface Constants
{
	public static final String	      PROJECT_NAME	   = "Snova";
	public static final String	      APP_HOME	       = "SNOVA_HOME";
	public static final String	      CONF_FILE	       = "snova.conf";
	public static final String	      PLUGIN_DESC_FILE	= "plugin.xml";
	public static final String	      FILE_SP	       = System.getProperty("file.separator");
	
	public static final String	      OFFICIAL_SITE	   = "http://snova.googlecode.com";
	public static final String	      OFFICIAL_TWITTER	= "http://twitter.com/yinqiwen";
	
	public static final ChannelBuffer	CRLF_CHARS	   = ChannelBuffers
	                                                           .wrappedBuffer("\r\r\r\r\r\r\r\n\r\r\r\r\r"
	                                                                   .getBytes());
}
