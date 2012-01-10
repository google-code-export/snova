/**
 * 
 */
package org.snova.spac.session;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.spac.config.SpacConfig;
import org.snova.spac.service.HostsService;

/**
 * @author qiyingwang
 * 
 */
public class HostsFowwardSession extends ForwardSession
{
//	private String proxyHost;
//	private int proxyPort;
//	private String proxyUser;
//	private String proxyPass;
	public HostsFowwardSession(String sessionname)
	{
		super("127.0.0.1:0");
	}

	@Override
	public SessionType getType()
	{
		// TODO Auto-generated method stub
		return SessionType.FORWARD;
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

	@Override
	public void onEvent(EventHeader header, Event event)
	{
		if (logger.isDebugEnabled())
		{
			logger.debug("Handler event:" + header.type + " in direct session");
		}
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) event;
				SimpleSocketAddress addr = getRemoteAddress((HTTPRequestEvent) event);
				remoteChannel = getRemoteChannel(addr.host, addr.port);
				if (req.method.equalsIgnoreCase("Connect"))
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1,
					        remoteChannel != null ? HttpResponseStatus.OK
					                : HttpResponseStatus.SERVICE_UNAVAILABLE);
					ChannelFuture future = localChannel.write(res);
					removeCodecHandler(localChannel, future);
					return;
				}
				if (null == remoteChannel)
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1,
					        HttpResponseStatus.SERVICE_UNAVAILABLE);
					localChannel.write(res);
				}
				else
				{
					SimpleSocketAddress hostaddr = getRemoteAddressFromRequestEvent(req);
					if(SpacConfig.getInstance().isHttpsOnlyHost(hostaddr.host))
					{
						HttpResponse redirect = new DefaultHttpResponse(
						        HttpVersion.HTTP_1_1,
						        HttpResponseStatus.MOVED_PERMANENTLY);
						String url = req.url;
						if(!url.startsWith("http://"))
						{
							url = "http://" + hostaddr.host + url;
						}
						redirect.addHeader("Location", url.replace("http://", "https://"));
						localChannel.write(redirect);
					}
					else
					{
						ChannelBuffer msg = buildRequestChannelBuffer((HTTPRequestEvent) event);
						remoteChannel.write(msg);
					}
				}
				break;
			}
			default:
			{
				super.onEvent(header, event);
			}
		}
	}
}
