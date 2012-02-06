/**
 * 
 */
package org.snova.framework.util.proxy;

import org.arch.util.StringHelper;

/**
 * @author wqy
 * 
 */
public class ProxyInfo
{
	public String	 host;
	public int	     port	 = 80;
	public String	 user;
	public String	 passwd;
	public ProxyType	type	= ProxyType.HTTP;
	
	public boolean parse(String line)
	{
		if (null == line || line.trim().isEmpty())
		{
			return false;
		}
		line = line.trim();
		if (line.startsWith("http://"))
		{
			type = ProxyType.HTTP;
			port = 80;
			line = line.substring("http://".length());
		}
		else if (line.startsWith("https://"))
		{
			type = ProxyType.HTTPS;
			port = 443;
			line = line.substring("https://".length());
		}
		else
		{
			type = ProxyType.HTTP;
			port = 80;
		}
		String hostport = line;
		String userpass = null;
		if (line.lastIndexOf('@') != -1)
		{
			userpass = line.substring(0, line.lastIndexOf('@'));
			hostport = line.substring(line.lastIndexOf('@') + 1);
		}
		if (null != userpass)
		{
			String[] ks = StringHelper.split(userpass, ':');
			user = ks[0];
			if (ks.length > 1)
			{
				passwd = ks[1];
			}
		}
		String[] ss = StringHelper.split(hostport, ':');
		host = ss[0];
		if (ss.length > 1)
		{
			port = Integer.parseInt(ss[1]);
		}
		return true;
	}
	
	public String toString()
	{
		String schema = "http://";
		if (type.equals(ProxyType.HTTPS))
		{
			schema = "https://";
		}
		StringBuilder buffer = new StringBuilder();
		buffer.append(schema);
		if (null != user)
		{
			buffer.append(user);
			if (null != passwd)
			{
				buffer.append(":").append(passwd);
			}
			buffer.append("@");
		}
		buffer.append(host);
		if ((port != 80 && type.equals(ProxyType.HTTP))
		        || (port != 443 && type.equals(ProxyType.HTTPS)))
		{
			buffer.append(":").append(port);
		}
		return buffer.toString();
	}
}
