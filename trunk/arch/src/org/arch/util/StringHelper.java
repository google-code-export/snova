/**
 * 
 */
package org.arch.util;

import java.util.ArrayList;

/**
 * @author wqy
 *
 */
public class StringHelper
{
	public static String[] split(String str, char ch)
	{
		ArrayList<String> ss = new ArrayList<String>();
		String k = str;
		int index = k.indexOf(ch);
		while(index != -1)
		{
			ss.add(k.substring(0, index));
			if(index < k.length() - 1)
			{
				k = k.substring(index + 1);
				index = k.indexOf(ch);
			}
			else
			{
				k= null;
				break;
			}
		}
		if(null != k && !k.isEmpty())
		{
			ss.add(k);
		}
		String[] ret = new String[ss.size()];
		return ss.toArray(ret);
	}
	
	public static String[] split(String str, String substr)
	{
		ArrayList<String> ss = new ArrayList<String>();
		String k = str;
		int index = k.indexOf(substr);
		while(index != -1)
		{
			ss.add(k.substring(0, index));
			if(index < k.length() - 1)
			{
				k = k.substring(index + 1);
				index = k.indexOf(substr);
			}
			else
			{
				k= null;
				break;
			}
		}
		if(null != k && !k.isEmpty())
		{
			ss.add(k);
		}
		return null;
	}
}
