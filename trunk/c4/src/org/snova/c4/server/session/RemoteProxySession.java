/**
 * 
 */
package org.snova.c4.server.session;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.Event;
import org.arch.event.EventConstants;
import org.arch.event.EventDispatcher;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.misc.CompressEvent;
import org.arch.event.misc.CompressEventV2;
import org.arch.event.misc.CompressorType;
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.SocketConnectionEvent;
import org.snova.c4.common.event.TCPChunkEvent;
import org.snova.c4.common.event.UserLoginEvent;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public class RemoteProxySession extends SimpleChannelUpstreamHandler
{
	protected static Logger logger = LoggerFactory
	        .getLogger(RemoteProxySession.class);
	private static boolean inited = false;
	private static ClientSocketChannelFactory factory;
	private static Map<String, Map<Integer, RemoteProxySession>> sessionTable = new HashMap<String, Map<Integer, RemoteProxySession>>();
	private static Map<String, ArrayList<LinkedBlockingDeque<Event>>> sendEvents = new HashMap<String, ArrayList<LinkedBlockingDeque<Event>>>();

	public static void init()
	{
		if (!inited)
		{
			C4Events.init(null, true);
			inited = true;
		}
	}

	public static void touch(String user, int poolSize)
	{
		ArrayList<LinkedBlockingDeque<Event>> evss = sendEvents.get(user);
		if (null == evss)
		{
			evss = new ArrayList<LinkedBlockingDeque<Event>>();
			sendEvents.put(user, evss);
		}
		while (evss.size() < poolSize)
		{
			evss.add(new LinkedBlockingDeque<Event>(1024));
		}
	}

	public static void extractEventResponses(String user, int index,
	        Buffer buf, int maxSize)
	{
		// System.out.println("request event to " + index);
		ArrayList<LinkedBlockingDeque<Event>> evss = sendEvents.get(user);
		if (evss.size() > index)
		{
			LinkedBlockingDeque<Event> queue = evss.get(index);
			while (!queue.isEmpty() && buf.readableBytes() <= maxSize)
			{
				Event ev = queue.removeFirst();
				// System.out.println("Send event");
				ev.encode(buf);
			}
		}
	}

	private static Event extractEvent(Event ev)
	{
		while (Event.getTypeVersion(ev.getClass()).type == EventConstants.COMPRESS_EVENT_TYPE
		        || Event.getTypeVersion(ev.getClass()).type == EventConstants.ENCRYPT_EVENT_TYPE)
		{
			if (ev instanceof CompressEventV2)
			{
				ev = ((CompressEventV2) ev).ev;
			}
			if (ev instanceof EncryptEventV2)
			{
				ev = ((EncryptEventV2) ev).ev;
			}
			if (ev instanceof CompressEvent)
			{
				ev = ((CompressEvent) ev).ev;
			}
			if (ev instanceof EncryptEvent)
			{
				ev = ((EncryptEvent) ev).ev;
			}
		}
		return ev;
	}

	protected static ChannelBuffer buildRequestChannelBuffer(HTTPRequestEvent ev)
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

	private static void offerSendEvent(String user, Event event)
	{
		ArrayList<LinkedBlockingDeque<Event>> evss = sendEvents.get(user);
		if (event instanceof TCPChunkEvent)
		{
			CompressEventV2 compress = new CompressEventV2();
			compress.ev = event;
			compress.type = CompressorType.SNAPPY;
			compress.setHash(event.getHash());
			event = compress;
		}
		EncryptEventV2 encrypt = new EncryptEventV2();
		encrypt.type = EncryptType.SE1;
		encrypt.ev = event;
		encrypt.setHash(event.getHash());
		int index = event.getHash() % evss.size();
		// System.out.println("offer event to " + index);
		evss.get(index).offer(encrypt);
	}

	protected synchronized static ClientSocketChannelFactory getClientSocketChannelFactory()
	{
		if (null == factory)
		{
			ExecutorService workerExecutor = SharedObjectHelper
			        .getGlobalThreadPool();
			if (null == workerExecutor)
			{
				workerExecutor = Executors.newFixedThreadPool(25);
				SharedObjectHelper.setGlobalThreadPool(workerExecutor);
			}
			factory = new NioClientSocketChannelFactory(workerExecutor,
			        workerExecutor);
		}
		return factory;
	}

	private static void destorySession(RemoteProxySession session)
	{
		if(sessionTable.get(session.user) != null)
		{
			sessionTable.get(session.user).remove(session.sessionId);
		}
	}
	private static RemoteProxySession getSession(String user, Event ev)
	{
		Map<Integer, RemoteProxySession> table = sessionTable.get(user);
		if (null == table)
		{
			table = new ConcurrentHashMap<Integer, RemoteProxySession>();
			sessionTable.put(user, table);
		}
		RemoteProxySession session = table.get(ev.getHash());
		if (null == session)
		{
			session = new RemoteProxySession(user, ev.getHash());
			table.put(ev.getHash(), session);
		}
		return session;
	}

	private static void clearUser(String user)
	{

	}

	public static void dispatchEvent(String user, final Buffer content)
	        throws Exception
	{
		while (content.readable())
		{
			Event event = EventDispatcher.getSingletonInstance().parse(content);
			event = extractEvent(event);
			TypeVersion tv = Event.getTypeVersion(event.getClass());
			if (tv.type == C4Constants.EVENT_USER_LOGIN_TYPE)
			{
				UserLoginEvent usev = (UserLoginEvent) event;
				clearUser(usev.user);
			}
			else
			{
				getSession(user, event).handleEvent(tv, event);
			}
		}
	}

	private void handleEvent(TypeVersion tv, Event ev)
	{
		//System.out.println("handleEvent" + ev.getClass().getName());
		switch (tv.type)
		{
			case C4Constants.EVENT_TCP_CHUNK_TYPE:
			{
				if (null != client)
				{
					final TCPChunkEvent chunk = (TCPChunkEvent) ev;
					client.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							client.getChannel()
							        .write(ChannelBuffers
							                .wrappedBuffer(chunk.content));
						}
					});

				}
				break;
			}
			case C4Constants.EVENT_TCP_CONNECTION_TYPE:
			{
				SocketConnectionEvent event = (SocketConnectionEvent) ev;
				if (event.status == SocketConnectionEvent.TCP_CONN_CLOSED)
				{
					if (null != client)
					{
						client.getChannel().close();
					}
					destorySession(this);
				}
				break;
			}
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				final HTTPRequestEvent req = (HTTPRequestEvent) ev;
				String host = req.getHeader("Host");
				int port = 80;
				method = req.method;
				if (host.indexOf(":") != -1)
				{
					String[] ss = host.split(":");
					host = ss[0];
					port = Integer.parseInt(ss[1]);
				}
				else
				{
					if (method.equalsIgnoreCase("Connect"))
					{
						port = 443;
					}
				}
				getClientChannel(host, port);
				if (method.equalsIgnoreCase("Connect"))
				{
					client.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							TCPChunkEvent ev = new TCPChunkEvent();
							ev.setHash(ev.getHash());
							if (future.isSuccess())
							{
								ev.content = "HTTP/1.1 200 OK\r\n\r\n"
								        .getBytes();
							}
							else
							{
								ev.content = "HTTP/1.1 503 ServiceUnavailable\r\n\r\n"
								        .getBytes();
							}
							offerSendEvent(user, ev);
						}
					});
				}
				else
				{
					client.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							if (future.isSuccess())
							{
								client.getChannel().write(
								        buildRequestChannelBuffer(req));
							}
						}
					});
				}
				break;
			}
			default:
			{
				logger.error("Unsupported event type version " + tv.type + ":"
				        + tv.version);
				break;
			}
		}
	}

	private ChannelFuture getClientChannel(String host, int port)
	{
		String addr = host + ":" + port;
		if (addr.equals(remoteAddr) && null != client
		        && client.getChannel().isConnected())
		{
			return client;
		}
		if (null != client)
		{
			client.getChannel().close();
		}
		ChannelPipeline pipeline = Channels.pipeline();
		pipeline.addLast("handler", this);
		
		Channel ch = getClientSocketChannelFactory().newChannel(pipeline);
		client = ch.connect(new InetSocketAddress(host, port));
		remoteAddr = addr;
		addrMap.put(ch, remoteAddr);
		sequence = 0;
		return client;
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
	        throws Exception
	{
		SocketConnectionEvent ev = new SocketConnectionEvent();
		ev.setHash(sessionId);
		ev.addr = addrMap.get(e.getChannel());
		ev.status = SocketConnectionEvent.TCP_CONN_CLOSED;
		offerSendEvent(user, ev);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
	        throws Exception
	{
	    // TODO Auto-generated method stub
		//System.out.println("ex " + addrMap.get(e.getChannel()));
		//e.getCause().printStackTrace();
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
	        throws Exception
	{
		Object msg = e.getMessage();
		if (msg instanceof ChannelBuffer)
		{
			ChannelBuffer buf = (ChannelBuffer) msg;
			TCPChunkEvent ev = new TCPChunkEvent();
			ev.setHash(sessionId);
			ev.sequence = sequence;
			ev.content = new byte[buf.readableBytes()];
			buf.readBytes(ev.content);
			offerSendEvent(user, ev);
			sequence++;
		}
		else
		{
			logger.error("Unsupported message type:" + msg.getClass().getName());
		}
	}

	RemoteProxySession(String user, int hash)
	{
		this.user = user;
		this.sessionId = hash;
	}

	private int sessionId;
	private int sequence;
	private String method;
	private String user;
	private String remoteAddr;
	private Map<Channel, String> addrMap = new HashMap<Channel, String>();
	private ChannelFuture client = null;
}
