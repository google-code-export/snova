/**
 * 
 */
package org.snova.framework.proxy.gae;

import org.arch.util.StringHelper;

/**
 * @author wqy
 * 
 */
public class GAEServerAuth
{
	public String appid;
	public String user;
	public String passwd;
	public String token = "";

	public void init()
	{
		if (user == null || user.equals(""))
		{
			user = "anonymouse";
		}
		if (passwd == null || passwd.equals(""))
		{
			passwd = "anonymouse";
		}
		appid = appid.trim();
		user = user.trim();
		passwd = passwd.trim();
	}

	public boolean parse(String line)
	{
		if (null == line || line.trim().isEmpty())
		{
			return false;
		}
		line = line.trim();
		String[] ss = StringHelper.split(line, '@');
		if (ss.length == 1)
		{
			appid = line;
		}
		else
		{
			appid = ss[ss.length - 1];
			int index = line.indexOf("@" + appid);
			String userpass = line.substring(0, index);
			String[] ks = StringHelper.split(userpass, ':');
			user = ks[0];
			passwd = ks[1];
		}

		init();
		return true;
	}

	public String toString()
	{
		return user + ":" + passwd + "@" + appid;
	}
}
