/**
 * 
 */
package org.snova.c4.server.session.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;

import org.arch.event.Event;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.SocketConnectionEvent;
import org.snova.c4.common.event.SocketReadEvent;
import org.snova.c4.common.event.TCPChunkEvent;

/**
 * @author wqy
 * 
 */
public class RemoteProxySession
{
	private static Selector	                               selector;
	private static Map<String, LinkedBlockingDeque<Event>>	groupSendEvents	= new HashMap<String, LinkedBlockingDeque<Event>>();
	private static boolean	                               inited	        = false;
	private int	                                           groupIndex;
	private int	                                           sequence;
	private String	                                       method;
	private String	                                       user;
	private String	                                       remoteAddr;
	private volatile SocketChannel	                       client	        = null;
	
	public static void init()
	{
		if (!inited)
		{
			C4Events.init(null, true);
			try
			{
				selector = Selector.open();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			inited = true;
		}
	}
	
	private boolean writeContent(byte[] content)
	{
		if (null != client)
		{
			try
			{
				client.write(ByteBuffer.wrap(content));
				return true;
			}
			catch (IOException e)
			{
				close(client, remoteAddr);
				return false;
			}
		}
		return false;
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
				//return readClient(event.maxread, event.timeout);
			}
			case C4Constants.EVENT_TCP_CONNECTION_TYPE:
			{
				SocketConnectionEvent event = (SocketConnectionEvent) ev;
				if (event.status == SocketConnectionEvent.TCP_CONN_CLOSED)
				{
					
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
						// System.out.println("Session[" + sessionId +
						// "]####establised to  " + remoteAddr + " at" +
						// System.currentTimeMillis()/1000);
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
					if (success)
					{
						return writeContent(buildRequestContent(req));
					}
					else
					{
						
					}
				}
				break;
			}
			default:
			{
//				logger.error("Unsupported event type version " + tv.type + ":"
//				        + tv.version);
				return false;
			}
		}
		return true;
	}
}
