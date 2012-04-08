/**
 * 
 */
package org.snova.c4.client.connection.rsocket;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.common.event.RSocketAcceptedEvent;

/**
 * @author qiyingwang
 * 
 */
public class RSocketHandler extends SimpleChannelUpstreamHandler
{
	protected static Logger	               logger	         = LoggerFactory
            .getLogger(RSocketHandler.class);
	//private C4ServerAuth auth;
	private RSocketProxyConnection conn;
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
	        throws Exception
	{
		if(null != conn)
		{
			conn.closeRConnection();
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
	        throws Exception
	{
		logger.error("RSocketChannel exception caught:", e.getCause());
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
	        throws Exception
	{
		Object msg = e.getMessage();
		if(msg instanceof ChannelBuffer)
		{
			ChannelBuffer buf = (ChannelBuffer) msg;
			byte[] raw = new byte[buf.readableBytes()];
			buf.readBytes(raw);
			Buffer content = Buffer.wrapReadableContent(raw);
			while(content.readable())
			{
				Event ev = EventDispatcher.getSingletonInstance().parse(content);
				if(ev instanceof RSocketAcceptedEvent)
				{
					
					RSocketAcceptedEvent rev = (RSocketAcceptedEvent) ev;
					if(logger.isDebugEnabled())
					{
						logger.debug("Recv connection from " + rev.domain + ":" + rev.port);
					}
					C4ServerAuth auth = new C4ServerAuth();
					auth.domain = rev.domain;
					auth.port = rev.port;
					conn = RSocketProxyConnection.saveRConnection(auth, ctx.getChannel());
				}
				else
				{
					conn.handleEvent(ev);
				}
			}
		}
		else
		{
			logger.error("Unsupported message type:" + msg.getClass().getName());
		}
	}
}
