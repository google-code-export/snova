/**
 * 
 */
package org.snova.framework.proxy.spac;

import java.util.regex.Pattern;

import org.arch.util.StringHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author qiyingwang
 * 
 */
public class SpacRule
{
	private Pattern[] urlPatterns = new Pattern[0];
	private Pattern[] hostPatterns = new Pattern[0];
	private Pattern[] methodPatterns = new Pattern[0];
	private String protocolPattern;

	private String[] proxyies = new String[0];
	private String[] attrs = new String[0];
	private String[] filters = new String[0];

	private static String[] toStringArray(JSONArray array) throws JSONException
	{
		String[] ss = new String[array.length()];
		for (int i = 0; i < array.length(); i++)
		{
			ss[i] = array.getString(i);
		}
		return ss;
	}

	public SpacRule(JSONObject obj) throws JSONException
	{
		if (obj.has("URL"))
		{
			JSONArray urls = obj.getJSONArray("URL");
			String[] ss = toStringArray(urls);
			urlPatterns = StringHelper.prepareRegexPattern(ss);
		}
		if (obj.has("Host"))
		{
			JSONArray hosts = obj.getJSONArray("Host");
			String[] ss = toStringArray(hosts);
			hostPatterns = StringHelper.prepareRegexPattern(ss);
		}
		if (obj.has("Method"))
		{
			JSONArray ms = obj.getJSONArray("Method");
			String[] ss = toStringArray(ms);
			methodPatterns = StringHelper.prepareRegexPattern(ss);
		}
		if (obj.has("Protocol"))
		{
			protocolPattern = obj.getString("Protocol");
		}
		if (obj.has("Proxy"))
		{
			JSONArray ms = obj.getJSONArray("Proxy");
			proxyies = toStringArray(ms);
		}
		if (obj.has("Attr"))
		{
			JSONArray ms = obj.getJSONArray("Attr");
			attrs = toStringArray(ms);
		}
		if (obj.has("Filter"))
		{
			JSONArray ms = obj.getJSONArray("Filter");
			filters = toStringArray(ms);
		}
	}
}
