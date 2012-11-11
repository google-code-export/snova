/**
 * 
 */
package org.arch.dns;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;

/**
 * @author wqy
 * 
 */
public class Resolver
{
	
	private static class CacheItem
	{
		String[]	ips;
		long		retirveTime;
		int		 ttl;
	}
	
	private static Map<String, CacheItem>	cache	= new ConcurrentHashMap<String, CacheItem>();
	
	public static String[] resolveIPv4(String[] dnsServer, String host,
	        ResolveOptions option) throws NamingException
	{
		if (null == option)
		{
			option = new ResolveOptions();
		}
		if (option.cacheTtl != 0)
		{
			CacheItem item = cache.get(host);
			if (null != item)
			{
				if (option.cacheTtl == ResolveOptions.DNS_CACHE_TTL_FOREVER
				        || (item.retirveTime + item.ttl >= System
				                .currentTimeMillis()))
				{
					return item.ips;
				}
			}
			
		}
		DnsClient client = new DnsClient(dnsServer, option.retry,
		        option.timeout);
		ResourceRecords records = client.query(new DnsName(host),
		        ResourceRecord.TYPE_A, ResourceRecord.CLASS_INTERNET,
		        option.useTcp, true, false);
		List<String> list = new ArrayList<String>();
		int ttl = option.cacheTtl > 0 ? option.cacheTtl : 3600 * 1000;
		for (int i = 0; i < records.answer.size(); i++)
		{
			if (records.answer.get(i) instanceof ResourceRecord)
			{
				ResourceRecord rec = (ResourceRecord) records.answer.get(i);
				if (rec.rrtype == ResourceRecord.TYPE_A)
				{
					if (rec.ttl > ttl)
					{
						ttl = rec.ttl;
					}
					list.add(rec.rdata.toString());
				}
			}
		}
		String[] ips = list.toArray(new String[list.size()]);
		if (option.cacheTtl != 0)
		{
			CacheItem item = new CacheItem();
			item.ips = ips;
			item.ttl = ttl;
			item.retirveTime = System.currentTimeMillis();
			cache.put(host, item);
		}
		return ips;
	}
}
