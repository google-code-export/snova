/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: PluginDescription.java 
 *
 * @author yinqiwen [ 2010-6-15 | 03:19:20 PM ]
 *
 */
package org.snova.framework.plugin;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.httpserver.HttpLocalProxyServer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 */

public class PluginDescription
{
	protected static Logger	logger	     = LoggerFactory
            .getLogger(PluginDescription.class);
	public String	    name;
	
	public String	    version;
	
	public String	    description;
	
	public String	    entryClass;
	
	public List<String>	depends	= new LinkedList<String>();
	
	public boolean parse(InputStream is)
	{
		try
		{
			DocumentBuilderFactory factory = DocumentBuilderFactory
			        .newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(is);
			Element root = doc.getDocumentElement();
			name = root.getAttribute("name");
			version = root.getAttribute("version");
			Node entryClassNode = root.getElementsByTagName("entryClass").item(
			        0);
			entryClass = entryClassNode.getTextContent().trim();
			Node descriptionNode = root.getElementsByTagName("description")
			        .item(0);
			description = descriptionNode.getTextContent().trim();
			
			NodeList list = root.getElementsByTagName("depend");
			for (int i = 0; i < list.getLength(); i++)
			{
				Node node = list.item(i);
				if (!node.getTextContent().trim().isEmpty())
				{
					depends.add(node.getTextContent().trim());
				}
			}
			return true;
		}
		catch (Exception e)
		{
			logger.error("Failed to parse plugin desc.", e);
			return false;
		}
		
	}
}
