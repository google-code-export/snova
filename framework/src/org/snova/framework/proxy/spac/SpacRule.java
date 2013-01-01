/**
 * 
 */
package org.snova.framework.proxy.spac;

import java.util.regex.Pattern;

import org.arch.util.StringHelper;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author yinqiwen
 * 
 */
public class SpacRule
{
	private Pattern[]	urlPatterns	   = new Pattern[0];
	private Pattern[]	hostPatterns	= new Pattern[0];
	private String[]	methodPatterns	= new String[0];
	private String	  protocolPattern;
	
	String[]	      proxyies	       = new String[0];
	String[]	      attrs	           = new String[0];
	String[]	      filters	       = new String[0];
	
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
			methodPatterns = toStringArray(ms);
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
	
	private boolean matchUrl(HttpRequest req)
	{
		if (urlPatterns.length == 0)
		{
			return true;
		}
		String url = req.getUri();
		if (!req.getMethod().equals(HttpMethod.CONNECT))
		{
			if (!url.startsWith("http://"))
			{
				url = HttpHeaders.getHost(req) + url;
			}
		}
		for (Pattern p : urlPatterns)
		{
			if (p.matcher(url).matches())
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean matchHost(HttpRequest req)
	{
		if (hostPatterns.length == 0)
		{
			return true;
		}
		String host = HttpHeaders.getHost(req);
		for (Pattern p : hostPatterns)
		{
			if (p.matcher(host).matches())
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean matchMethod(HttpRequest req)
	{
		if (methodPatterns.length == 0)
		{
			return true;
		}
		String m = req.getMethod().getName();
		for (String ms : methodPatterns)
		{
			if (m.equalsIgnoreCase(ms))
			{
				return true;
			}
		}
		return false;
	}
	
	private boolean matchProtocol(HttpRequest req)
	{
		if (null == protocolPattern)
		{
			return true;
		}
		String protocol = "http";
		if (req.getMethod().equals(HttpMethod.CONNECT))
		{
			protocol = "https";
		}
		return protocol.equalsIgnoreCase(protocolPattern);
	}
	
	public boolean match(HttpRequest req)
	{
		return matchUrl(req) && matchHost(req) && matchMethod(req)
		        && matchProtocol(req);
	}
}
