package org.arch.util;

import static org.junit.Assert.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import org.arch.config.IniProperties;
import org.junit.Test;

public class PropertiesHelperTest
{

	@Test
	public void testReplaceSystemProperties() throws IOException
	{
//		Properties props = new Properties();
//		props.load(PropertiesHelperTest.class.getResourceAsStream("logging properties"));
//		System.setProperty("HYK_PROXY_HOME", "#####");
//		int ret = PropertiesHelper.replaceSystemProperties(props);
//		assertEquals("#####/log/hyk-proxy.log", props.getProperty("java.util.logging.FileHandler.pattern"));
//		assertEquals(1, ret);
		
		String[] splits = "Xyz|as|xfas|wer".split("[,|;|\\|]");
		System.out.println(Arrays.asList(splits));
		
		IniProperties ini = new IniProperties();
		ini.setProperty("Hrlo", "sda", "safas");
		ini.store(new FileOutputStream("test.txt"));
	}

}
