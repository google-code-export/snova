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
	
	public static Pattern prepareRegexPattern(String ss)
	{
		String s = ss.replace(".", "\\.");
		s = s.replace("*", ".*");
		return Pattern.compile(s);
	}

	public static Pattern[] prepareRegexPatterns(String[] ss)
	{
		Pattern[] ps = new Pattern[ss.length];
		for (int i = 0; i < ss.length; i++)
		{
			ps[i] = prepareRegexPattern(ss[i]);
		}
		return ps;
	}
	
	public static boolean wildCardMatch(String text, String pattern)
    {
        // Create the cards by splitting using a RegEx. If more speed 
        // is desired, a simpler character based splitting can be done.
        String [] cards = pattern.split("\\*");

        // Iterate over the cards.
        for (String card : cards)
        {
            int idx = text.indexOf(card);
            
            // Card not detected in the text.
            if(idx == -1)
            {
                return false;
            }
            
            // Move ahead, towards the right of the text.
            text = text.substring(idx + card.length());
        }
        
        return true;
    }
}
