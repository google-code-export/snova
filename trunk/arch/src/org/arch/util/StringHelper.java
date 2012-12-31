/**
 * 
 */
package org.arch.util;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * @author wqy
 * 
 */
public class StringHelper
{
	public static boolean isEmptyString(String str)
	{
		if (null == str)
		{
			return true;
		}
		return str.trim().length() == 0;
	}

	public static boolean containsString(String str, String[] ss)
	{
		if (null == ss)
		{
			return false;
		}
		for (String s : ss)
		{
			if (str.indexOf(s) != -1)
			{
				return true;
			}
		}
		return false;
	}

	public static String[] split(String str, char ch)
	{
		ArrayList<String> ss = new ArrayList<String>();
		String k = str;
		int index = k.indexOf(ch);
		while (index != -1)
		{
			ss.add(k.substring(0, index));
			if (index < k.length() - 1)
			{
				k = k.substring(index + 1);
				index = k.indexOf(ch);
			}
			else
			{
				k = null;
				break;
			}
		}
		if (null != k && !k.isEmpty())
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
		while (index != -1)
		{
			ss.add(k.substring(0, index));
			if (index < k.length() - 1)
			{
				k = k.substring(index + 1);
				index = k.indexOf(substr);
			}
			else
			{
				k = null;
				break;
			}
		}
		if (null != k && !k.isEmpty())
		{
			ss.add(k);
		}
		return null;
	}

	public static Pattern[] prepareRegexPattern(String[] ss)
	{
		Pattern[] ps = new Pattern[ss.length];
		for (int i = 0; i < ss.length; i++)
		{
			String s = ss[i].replace(".", "\\.");
			s = s.replace("*", ".*");
			ps[i] = Pattern.compile(s);
		}
		return ps;
	}
}
