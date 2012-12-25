package org.arch.util;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

public class StringHelperTest
{

	@Test
	public void testSplitStringChar()
	{
		int loopcount = 1000000;
		String str = "53930,WWW.YTBBS.COM,9699523208,s2521193541,221.214.241.231,2012-01-22,23:30:21,/thread-2082863-1-1.html,www.ytbbs.com/forum.php,-,-,mod%3Dforumdisplay%26fid%3D379%26page%3D1,513451,2,forum,viewthread,379,2082863,,1,,011,Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; SV1; 360SE),1024x600,32-bit,Win32,10.0,lan,zh-cn,1,-8,,,,117014756,1064922,4188,,,bc=1;adid=";
		long start = System.currentTimeMillis();
		for (int i = 0; i < loopcount; i++)
		{
			String[] ss = StringHelper.split(str, ',');
			
		}
		long end = System.currentTimeMillis();
		System.out.println("Cost " + (end - start) + "ms to split " + loopcount
		        + " times.");
		start = System.currentTimeMillis();
		for (int i = 0; i < loopcount; i++)
		{
			String[] ss = str.split(",");
		}
		end = System.currentTimeMillis();
		System.out.println("Cost " + (end - start) + "ms to split " + loopcount
		        + " times.");

		String[] ss = StringHelper.split(str, '@');
		System.out.println(Arrays.toString(ss));
	}

}
