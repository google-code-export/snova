/**
 * 
 */
package org.arch.misc.gfw;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import org.arch.misc.crypto.base64.Base64;

/**
 * @author wqy
 * 
 */
public class GFWList
{
	public static GFWList parse(String content)
	{
		return new GFWList(new String(Base64.decodeFast(content.trim())));
	}
	
	private GFWListRuleTable	ruleTable	= new GFWListRuleTable();
	
	private GFWList(String text)
	{
		StringReader sr = new StringReader(text);
		BufferedReader br = new BufferedReader(sr);
		try
		{
			while (true)
			{
				String line = br.readLine();
				if (null == line)
				{
					break;
				}
				line = line.trim();
				if (line.startsWith("!"))// commnet
				{
					continue;
				}
				if (line.startsWith("@@||"))
				{
					ruleTable.addExcludeSet(line.substring("@@||".length()));
				}
				else if (line.startsWith("||"))
				{
					ruleTable.addHostMatch(line.substring("||".length()));
				}
				else if (line.startsWith("|http"))
				{
					ruleTable.addURLStartMatch(line.substring("|".length()));
				}
				else if (line.startsWith("/"))
				{
					ruleTable.addRegexMatch(line.substring("/".length()));
				}
				else
				{
					if(!line.isEmpty())
					{
						ruleTable.addHTTPStrMatch(line);
					}
				}	
			}
		}
		catch (IOException e)
		{
			// nothing to do
		}
		
	}
	
	public boolean isBlockedByGFW(String url)
	{
		return ruleTable.isBlockedByGFW(url);
	}
}
