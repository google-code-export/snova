/**
 * 
 */
package org.snova.spac.session;

import org.arch.event.http.HTTPRequestEvent;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.spac.service.HostsService;

/**
 * @author qiyingwang
 * 
 */
public class HostsFowwardSession extends DirectSession
{
//	private String proxyHost;
//	private int proxyPort;
//	private String proxyUser;
//	private String proxyPass;
	public HostsFowwardSession()
	{
		//super("127.0.0.1:0");
	}

	@Override
	public SessionType getType()
	{
		// TODO Auto-generated method stub
		return SessionType.DIRECT;
	}

	@Override
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent req)
	{
		SimpleSocketAddress addr = getRemoteAddressFromRequestEvent(req);
		String mapping = HostsService.getMappingHostIPV4(addr.host);
		if(null != mapping)
		{
			addr.host = mapping;
		}
		return addr;
	}
}
