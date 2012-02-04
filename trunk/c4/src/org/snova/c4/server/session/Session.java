package org.snova.c4.server.session;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SimpleSocketAddress;
import org.snova.framework.util.SharedObjectHelper;

public abstract class Session
{
	protected static Logger	                  logger	= LoggerFactory
	                                                         .getLogger(Session.class);
	private static ClientSocketChannelFactory	factory;
	
	protected static ClientSocketChannelFactory getClientSocketChannelFactory()
	{
		if (null == factory)
		{
			ExecutorService workerExecutor = Executors.newFixedThreadPool(25);
			//ThreadPoolExecutor workerExecutor = new OrderedMemoryAwareThreadPoolExecutor(
			//        25, 0, 0);
			SharedObjectHelper.setGlobalThreadPool(workerExecutor);
			factory = new NioClientSocketChannelFactory(workerExecutor,
			        workerExecutor);
		}
		return factory;
	}
	
	protected static Buffer buildRequestBuffer(HTTPRequestEvent ev)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(ev.method).append(" ").append(ev.url).append(" ")
		        .append("HTTP/1.1\r\n");
		for (KeyValuePair<String, String> header : ev.headers)
		{
			buffer.append(header.getName()).append(":")
			        .append(header.getValue()).append("\r\n");
		}
		buffer.append("\r\n");
		
		Buffer msg = new Buffer(buffer.length() + ev.content.readableBytes());
		msg.write(buffer.toString().getBytes());
		if (ev.content.readable())
		{
			msg.write(ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
			return msg;
		}
		else
		{
			return msg;
		}
	}
	
	protected static ChannelBuffer buildRequestChannelBuffer(HTTPRequestEvent ev)
	{
		return buildRequestChannelBuffer(ev, false);
	}
	
	protected static ChannelBuffer buildRequestChannelBuffer(
	        HTTPRequestEvent ev, boolean transparent)
	{
		SimpleSocketAddress addr = getRemoteAddressFromRequestEvent(ev);
		String pc = ev.getHeader("Proxy-Connection");
		if (pc != null)
		{
			ev.removeHeader("Proxy-Connection");
			ev.setHeader("Connection", pc);
		}
		if (!transparent)
		{
			// if (ev.url.startsWith("http://" + addr.host.trim()))
			// {
			// int start = "http://".length();
			// int end = ev.url.indexOf("/", start);
			// ev.url = ev.url.substring(end);
			// }
		}
		
		// if(ev.getContentLength() == 0)
		// {
		// ev.setHeader("Content-Length", "0");
		// }
		StringBuilder buffer = new StringBuilder();
		buffer.append(ev.method).append(" ").append(ev.url).append(" ")
		        .append("HTTP/1.1\r\n");
		for (KeyValuePair<String, String> header : ev.headers)
		{
			buffer.append(header.getName()).append(":")
			        .append(header.getValue()).append("\r\n");
		}
		buffer.append("\r\n");
		ChannelBuffer headerbuf = ChannelBuffers.wrappedBuffer(buffer
		        .toString().getBytes());
		if (ev.content.readable())
		{
			ChannelBuffer bodybuf = ChannelBuffers.wrappedBuffer(
			        ev.content.getRawBuffer(), ev.content.getReadIndex(),
			        ev.content.readableBytes());
			return ChannelBuffers.wrappedBuffer(headerbuf, bodybuf);
		}
		else
		{
			return headerbuf;
		}
	}
	
	protected static ChannelBuffer buildResponseBuffer(HttpResponse response)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(response.getProtocolVersion().toString()).append(" ")
		        .append(String.valueOf(response.getStatus().getCode()))
		        .append(" ")
		        .append(String.valueOf(response.getStatus().getReasonPhrase()))
		        .append("\r\n");
		for (Map.Entry<String, String> h : response.getHeaders())
		{
			buffer.append(h.getKey()).append(":").append(h.getValue())
			        .append("\r\n");
		}
		buffer.append("\r\n");
		ChannelBuffer headerbuf = ChannelBuffers.wrappedBuffer(buffer
		        .toString().getBytes());
		ChannelBuffer content = response.getContent();
		if (content.readable())
		{
			return ChannelBuffers.wrappedBuffer(headerbuf, content);
		}
		else
		{
			return headerbuf;
		}
	}
	
	protected static void removeCodecHandler(Channel channel)
	{
		if (null != channel.getPipeline().get("decoder"))
		{
			channel.getPipeline().remove("decoder");
		}
		if (null != channel.getPipeline().get("encoder"))
		{
			channel.getPipeline().remove("encoder");
		}
		if (null != channel.getPipeline().get("chunkedWriter"))
		{
			channel.getPipeline().remove("chunkedWriter");
		}
	}
	
	protected String	     name;
	protected int	         ID;
	
	protected SessionManager	sessionManager;
	
	Session(SessionManager sm)
	{
		this.sessionManager = sm;
	}
	
	public int getID()
	{
		return ID;
	}
	
	public void setID(int iD)
	{
		ID = iD;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
	}
	
	protected static SimpleSocketAddress getRemoteAddressFromRequestEvent(
	        HTTPRequestEvent req)
	{
		String host = req.getHeader("Host");
		if (null == host)
		{
			String url = req.url;
			if (url.startsWith("http://"))
			{
				url = url.substring(7);
				int next = url.indexOf("/");
				host = url.substring(0, next);
			}
			else
			{
				host = url;
			}
		}
		int index = host.indexOf(":");
		int port = 80;
		if (req.method.equalsIgnoreCase("Connect"))
		{
			port = 443;
		}
		String hostValue = host;
		if (index > 0)
		{
			hostValue = host.substring(0, index).trim();
			port = Integer.parseInt(host.substring(index + 1).trim());
		}
		
		SimpleSocketAddress addr = new SimpleSocketAddress(hostValue, port);
		return addr;
	}
	
	protected abstract void onEvent(EventHeader header, Event event);
	
	public abstract boolean routine();
	
	public abstract void close();
	
	public synchronized void handleEvent(EventHeader header, Event event)
	{
		setID(event.getHash());
		if (logger.isDebugEnabled())
		{
			logger.debug("Handler event:" + header.type + " in session["
			        + getID() + "] " + getName());
		}
		onEvent(header, event);
	}
}
