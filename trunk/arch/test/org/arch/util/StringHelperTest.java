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
		String str = "SAASFASF@asdasfas@afafasf@@asdasf@TTRR";
		long start = System.currentTimeMillis();
		for (int i = 0; i < loopcount; i++)
        {
			String[] ss = str.split("@");
        }
		long end = System.currentTimeMillis();
		System.out.println("Cost " + (end - start) + "ms to split " +loopcount+ " times." );
		start = System.currentTimeMillis();
		for (int i = 0; i < loopcount; i++)
        {
			String[] ss = StringHelper.split(str, '@');
        }
	    end = System.currentTimeMillis();
		System.out.println("Cost " + (end - start) + "ms to split " +loopcount+ " times." );
		
		String[] ss = StringHelper.split(str, '@');
		System.out.println(Arrays.toString(ss));
	}
	
}
