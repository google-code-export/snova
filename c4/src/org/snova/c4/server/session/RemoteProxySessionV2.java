/**
 * 
 */
package org.snova.c4.server.session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import org.arch.event.misc.EncryptEvent;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.SocketConnectionEvent;
import org.snova.c4.common.event.SocketReadEvent;
import org.snova.c4.common.event.TCPChunkEvent;
import org.snova.c4.common.event.UserLoginEvent;

/**
 * @author qiyingwang
 * 
 */
public class RemoteProxySessionV2
{
	protected static Logger	                                       logger	     = LoggerFactory
	                                                                                     .getLogger(RemoteProxySessionV2.class);
	private static boolean	                                       inited	     = false;
	private static Map<String, Map<Integer, RemoteProxySessionV2>>	sessionTable	= new ConcurrentHashMap<String, Map<Integer, RemoteProxySessionV2>>();
	private LinkedBlockingDeque<Event>	                           sendEvents	 = new LinkedBlockingDeque<Event>();
	
	public static void init()
	{
		if (!inited)
		{
			C4Events.init(null, true);
			inited = true;
		}
	}
	
	public void extractEventResponses(Buffer buf, int maxSize,
	        LinkedList<Event> evs)
	{
		while (!sendEvents.isEmpty() && buf.readableBytes() <= maxSize)
		{
			Event ev = sendEvents.removeFirst();
			ev.encode(buf);
			evs.add(ev);
		}
	}
	
	public void requeueEvents(LinkedList<Event> evs)
	{
		while (!evs.isEmpty())
		{
			Event ev = evs.removeLast();
			sendEvents.addFirst(ev);
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
	
	protected static byte[] buildRequestContent(HTTPRequestEvent ev)
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
		byte[] header = buffer.toString().getBytes();
		if (ev.content.readable())
		{
			byte[] all = new byte[header.length + ev.content.readableBytes()];
			System.arraycopy(header, 0, all, 0, header.length);
			ev.content.read(all, header.length, ev.content.readableBytes());
			return all;
		}
		else
		{
			return header;
		}
	}
	
	private void offerSendEvent(Event event)
	{
		EncryptEventV2 encrypt = new EncryptEventV2();
		encrypt.type = EncryptType.SE1;
		encrypt.ev = event;
		encrypt.setHash(event.getHash());
		sendEvents.add(encrypt);
	}
	
	private static void destorySession(RemoteProxySessionV2 session)
	{
		if (sessionTable.get(session.user) != null)
		{
			sessionTable.get(session.user).remove(session.sessionId).close();
		}
	}
	
	private static RemoteProxySessionV2 getSession(String user, Event ev)
	{
		Map<Integer, RemoteProxySessionV2> table = sessionTable.get(user);
		if (null == table)
		{
			table = new ConcurrentHashMap<Integer, RemoteProxySessionV2>();
			sessionTable.put(user, table);
		}
		RemoteProxySessionV2 session = table.get(ev.getHash());
		if (null == session)
		{
			session = new RemoteProxySessionV2(user, ev.getHash());
			table.put(ev.getHash(), session);
		}
		return session;
	}
	
	private static void clearUser(String user)
	{
		Map<Integer, RemoteProxySessionV2> ss = sessionTable.get(user);
		if (null != ss)
		{
			for (Map.Entry<Integer, RemoteProxySessionV2> entry : ss.entrySet())
			{
				entry.getValue().close();
			}
			ss.clear();
		}
	}
	
	public static RemoteProxySessionV2 dispatchEvent(String user,
	        final Buffer content) throws Exception
	{
		RemoteProxySessionV2 ret = null;
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
				RemoteProxySessionV2 s = getSession(user, event);
				s.handleEvent(tv, event);
				if (null != s)
				{
					ret = s;
				}
			}
		}
		return ret;
	}
	
	private boolean handleEvent(TypeVersion tv, Event ev)
	{
		
		switch (tv.type)
		{
			case C4Constants.EVENT_TCP_CHUNK_TYPE:
			{
				// System.out.println("#####recv chunk for " + remoteAddr);
				if (null != client)
				{
					final TCPChunkEvent chunk = (TCPChunkEvent) ev;
					return writeContent(chunk.content);
				}
				else
				{
					return false;
				}
			}
			case C4Constants.EVENT_SOCKET_READ_TYPE:
			{
				SocketReadEvent event = (SocketReadEvent) ev;
				return readClient(event.maxread, event.timeout);
			}
			case C4Constants.EVENT_TCP_CONNECTION_TYPE:
			{
				SocketConnectionEvent event = (SocketConnectionEvent) ev;
				if (event.status == SocketConnectionEvent.TCP_CONN_CLOSED)
				{
					close();
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
				boolean success = checkClient(host, port);
				if (method.equalsIgnoreCase("Connect"))
				{
					TCPChunkEvent chunk = new TCPChunkEvent();
					chunk.sequence = sequence;
					sequence++;
					chunk.setHash(sessionId);
					if (success)
					{
						chunk.content = "HTTP/1.1 200 OK\r\n\r\n".getBytes();
					}
					else
					{
						chunk.content = "HTTP/1.1 503 ServiceUnavailable\r\n\r\n"
						        .getBytes();
					}
					offerSendEvent(chunk);
				}
				else
				{
					return writeContent(buildRequestContent(req));
				}
				break;
			}
			default:
			{
				logger.error("Unsupported event type version " + tv.type + ":"
				        + tv.version);
				return false;
			}
		}
		return true;
	}
	
	private boolean checkClient(String host, int port)
	{
		String addr = host + ":" + port;
		if (addr.equals(remoteAddr) && null != client && client.isConnected())
		{
			return true;
		}
		try
		{
			System.out.println("Session[" + sessionId + "]####connect " + addr);
			// client = SocketChannel.open(new InetSocketAddress(host, port));
			// client.configureBlocking(true);
			client = new Socket(host, port);
			System.out.println("Session[" + sessionId + "]####connect success");
		}
		catch (IOException e)
		{
			e.printStackTrace();
			client = null;
			return false;
		}
		remoteAddr = addr;
		return true;
	}
	
	public boolean readClient(int maxread, int timeout)
	{
		if (null != client)
		{
			try
			{
				closing = false;
				// ByteBuffer buffer = ByteBuffer.allocate(maxread);
				byte[] buffer = new byte[maxread];
				client.setSoTimeout(timeout * 1000);
				// System.out.println("Session[" + sessionId +
				// "]start Read at");
				int n = client.getInputStream().read(buffer);
				// System.out.println("Session[" + sessionId + "]####Read " + n
				// + " for " + remoteAddr);
				if (n < 0)
				{
					close();
					return false;
				}
				if (n > 0)
				{
					byte[] content = new byte[n];
					System.arraycopy(buffer, 0, content, 0, n);
					TCPChunkEvent ev = new TCPChunkEvent();
					ev.setHash(sessionId);
					ev.sequence = sequence++;
					ev.content = content;
					offerSendEvent(ev);
				}
				return true;
			}
			catch (SocketTimeoutException e)
			{
				return false;
			}
			catch (IOException e)
			{
				close();
				return false;
			}
		}
		return false;
	}
	
	private boolean writeContent(byte[] content)
	{
		if (null != client)
		{
			try
			{
				client.getOutputStream().write(content);
				return true;
			}
			catch (IOException e)
			{
				close();
				return false;
			}
		}
		return false;
	}
	
	void close()
	{
		if (null != client)
		{
			try
			{
				client.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			client = null;
			SocketConnectionEvent ev = new SocketConnectionEvent();
			ev.setHash(sessionId);
			ev.addr = remoteAddr;
			ev.status = SocketConnectionEvent.TCP_CONN_CLOSED;
			offerSendEvent(ev);
			closing = true;
		}
	}
	
	public boolean isClosing()
	{
		boolean ret = closing;
		closing = false;
		return ret;
	}
	
	RemoteProxySessionV2(String user, int hash)
	{
		this.user = user;
		this.sessionId = hash;
	}
	
	private int	            sessionId;
	private int	            sequence;
	private String	        method;
	private String	        user;
	private String	        remoteAddr;
	private volatile Socket	client	= null;
	private boolean	        closing	= false;
}
