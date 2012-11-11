/**
 * 
 */
package org.arch.dns;

import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;

/**
 * @author wqy
 * 
 */
public class Resolver
{
	public static String[] resolveIPv4(String[] dnsServer, String host,
	        ResolveOptions option) throws NamingException
	{
		if (null == option)
		{
			option = new ResolveOptions();
		}
		DnsClient client = new DnsClient(dnsServer, option.retry,
		        option.timeout);
		ResourceRecords records = client.query(new DnsName(host),
		        ResourceRecord.TYPE_A, ResourceRecord.CLASS_INTERNET,
		        option.useTcp, true, false);
		List<String> list = new ArrayList<String>();
		for (int i = 0; i < records.answer.size(); i++)
		{
			if (records.answer.get(i) instanceof ResourceRecord)
			{
				ResourceRecord rec = (ResourceRecord) records.answer.get(i);
				if (rec.rrtype == ResourceRecord.TYPE_A)
				{
					list.add(rec.rdata.toString());
				}
			}
		}
		return list.toArray(new String[list.size()]);
	}
}
