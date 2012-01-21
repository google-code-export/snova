package org.arch.misc.gfw;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.Test;

public class GFWListTest
{
	
	@Test
	public void testIsBlockedByGFW() throws IOException
	{
		InputStream fis = getClass().getResourceAsStream("gfwlist.txt");
		BufferedReader br  = new BufferedReader(new InputStreamReader(fis));
		StringBuilder buffer = new StringBuilder();
		while(true)
		{
			String line = br.readLine();
			if(null == line)
			{
				break;
			}
			buffer.append(line);
		}
		GFWList gfwlist = GFWList.parse(buffer.toString());
		long start = System.currentTimeMillis();
		boolean ret = gfwlist.isBlockedByGFW("http://www.sina.com");
		assertFalse(ret);
		ret = gfwlist.isBlockedByGFW("https://www.youtube.com");
		assertTrue(ret);
		ret = gfwlist.isBlockedByGFW("http://kmh.gov.tw");
		assertFalse(ret);
		ret = gfwlist.isBlockedByGFW("https://www.youtube.com");
		assertTrue(ret);
		long end = System.currentTimeMillis();
		System.out.println("####Cost " + (end-start) + "ms");
	}
	
}
