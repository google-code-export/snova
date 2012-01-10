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

public class DirectSession extends ForwardSession
{
	public DirectSession()
    {
	    super("127.0.0.1:0");
    }

	@Override
    public SessionType getType()
    {
	    return SessionType.FORWARD;
    }
	
	@Override
	protected SimpleSocketAddress getRemoteAddress(HTTPRequestEvent req)
	{
	    return getRemoteAddressFromRequestEvent(req);
	}
	
	@Override
	public void onEvent(EventHeader header, Event event)
	{
		if(logger.isDebugEnabled())
		{
			logger.debug("Handler event:" + header.type + " in direct session");
		}
		switch (header.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) event;
				SimpleSocketAddress addr = getRemoteAddress((HTTPRequestEvent) event);
				Channel ch = getRemoteChannel(addr.host, addr.port);
				if (req.method.equalsIgnoreCase("Connect"))
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1, ch != null?HttpResponseStatus.OK:HttpResponseStatus.SERVICE_UNAVAILABLE);
					ChannelFuture future = localChannel.write(res);
					removeCodecHandler(localChannel, future);
					return;
				}
				if(null == ch)
				{
					HttpResponse res = new DefaultHttpResponse(
					        HttpVersion.HTTP_1_1, HttpResponseStatus.SERVICE_UNAVAILABLE);
					localChannel.write(res);
				}
				ChannelBuffer msg = buildRequestChannelBuffer((HTTPRequestEvent) event);
				ch.write(msg);
				break;
			}
			default:
			{
				super.onEvent(header, event);
			}
		}  
	}
}
