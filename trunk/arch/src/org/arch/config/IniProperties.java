/**
 * 
 */
package org.arch.config;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author qiyingwang
 * 
 */
public class IniProperties
{
	private Map<String, Properties>	propsTable	= new HashMap<String, Properties>();
	
	public void load(String file) throws IOException
	{
		FileInputStream fis = new FileInputStream(file);
		load(fis);
		fis.close();
	}
	
	public synchronized void load(InputStream in) throws IOException
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
		String line = null;
		String currentTag = "";
		while ((line = reader.readLine()) != null)
		{
			line = line.trim();
			if (line.equals("") || line.startsWith("#"))
			{
				continue;
			}
			
			if (line.startsWith("[") && line.endsWith("]"))
			{
				currentTag = line.substring(1, line.length() - 1).trim();
				continue;
			}
			String[] splits = line.split("=");
			if (splits.length == 2)
			{
				String key = splits[0].trim();
				String value = splits[1].trim();
				Properties props = propsTable.get(currentTag);
				if (null == props)
				{
					props = new Properties();
					propsTable.put(currentTag, props);
				}
				props.put(key, value);
			}
		}
	}
	
	public void store(OutputStream os) throws IOException
	{
		OutputStreamWriter writer = new OutputStreamWriter(os);
		for (String key : propsTable.keySet())
		{
			writer.append("[").append(key).append("]")
			        .append(System.getProperty("line.separator"));
			Properties props = propsTable.get(key);
			for (Object keyentry : props.keySet())
			{
				writer.append(keyentry.toString()).append("=")
				        .append(props.getProperty(keyentry.toString()))
				        .append(System.getProperty("line.separator"));
			}
			writer.append(System.getProperty("line.separator"));
		}
		writer.flush();
	}
	
	public void setProperty(String tagName, String key, String value)
	{
		Properties props = propsTable.get(tagName);
		if (null == props)
		{
			props = new Properties();
			propsTable.put(tagName, props);
		}
		props.setProperty(key, value);
	}
	
	public void setIntProperty(String tagName, String key, int value)
	{
		setProperty(tagName, key, Integer.toString(value));
	}
	
	public void setBoolProperty(String tagName, String key, boolean value)
	{
		setProperty(tagName, key, Boolean.toString(value));
	}
	
	public Properties getProperties(String tag)
	{
		return propsTable.get(tag);
	}
	
	public String getProperty(String tagName, String key)
	{
		Properties props = propsTable.get(tagName);
		if (null != props)
		{
			return props.getProperty(key);
		}
		return null;
	}
	
	public String getProperty(String tagName, String key, String defaultValue)
	{
		String v = getProperty(tagName, key);
		if (null != v)
		{
			return v;
		}
		return defaultValue;
	}
	
	public int getIntProperty(String tagName, String key)
	{
		String v = getProperty(tagName, key);
		if (null != v)
		{
			return Integer.parseInt(v);
		}
		throw new NumberFormatException("NULL value for " + tagName + ":" + key);
	}
	
	public int getIntProperty(String tagName, String key, int defaultValue)
	{
		String v = getProperty(tagName, key);
		if (null != v)
		{
			return Integer.parseInt(v);
		}
		return defaultValue;
	}
	
	public boolean getBoolProperty(String tagName, String key)
	{
		String v = getProperty(tagName, key);
		if (null != v)
		{
			return Boolean.parseBoolean(v);
		}
		throw new NumberFormatException("NULL value for " + tagName + ":" + key);
	}
	
	public boolean getBoolProperty(String tagName, String key,
	        boolean defaultValue)
	{
		String v = getProperty(tagName, key);
		if (null != v)
		{
			return Boolean.parseBoolean(v);
		}
		return defaultValue;
	}
}
