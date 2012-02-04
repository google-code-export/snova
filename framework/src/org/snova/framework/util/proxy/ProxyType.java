/**
 * 
 */
package org.snova.framework.util.proxy;

/**
 * @author wqy
 * 
 */
public enum ProxyType
{
	HTTP("http"), HTTPS("https");
	String	value;
	
	ProxyType(String v)
	{
		value = v;
		
	}
	
	public static ProxyType fromStr(String str)
	{
		if (str.equalsIgnoreCase("http"))
		{
			return HTTP;
		}
		if (str.equalsIgnoreCase("https"))
		{
			return HTTPS;
		}
		return HTTP;
	}
}
