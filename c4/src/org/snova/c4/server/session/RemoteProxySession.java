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
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.TypeVersion;
import org.arch.event.http.HTTPEventContants;
import org.arch.event.http.HTTPRequestEvent;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author qiyingwang
 * 
 */
public class RemoteProxySession extends SimpleChannelUpstreamHandler
{
	protected static Logger logger = LoggerFactory
	        .getLogger(RemoteProxySession.class);
	private static ClientSocketChannelFactory factory;
	private static Map<String, Map<Integer, RemoteProxySession>> sessionTable = new HashMap<String, Map<Integer, RemoteProxySession>>();
	private static Map<String, ArrayList<LinkedBlockingDeque<Event>>> sendEvents = new HashMap<String, ArrayList<LinkedBlockingDeque<Event>>>();

	private static void offerSendEvent(String user, int connIdx, Event event)
	{
		ArrayList<LinkedBlockingDeque<Event>> evss = sendEvents.get(user);
		if (null == evss)
		{
			evss = new ArrayList<LinkedBlockingDeque<Event>>();
			sendEvents.put(user, evss);
		}
		while (connIdx > evss.size())
		{
			evss.add(new LinkedBlockingDeque(1024));
		}
		evss.get(connIdx).offer(event);
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
			session = new RemoteProxySession();
			table.put(ev.getHash(), session);
		}
		return session;
	}

	public static void dispatchEvent(String user, final Buffer content)
	        throws Exception
	{
		while (content.readable())
		{
			Event event = EventDispatcher.getSingletonInstance().parse(content);
			getSession(user, event).handleEvent(event);
		}
	}

	private void handleEvent(Event ev)
	{
		TypeVersion tv = Event.getTypeVersion(ev.getClass());
		switch (tv.type)
		{
			case HTTPEventContants.HTTP_REQUEST_EVENT_TYPE:
			{
				HTTPRequestEvent req = (HTTPRequestEvent) ev;
				String host = req.getHeader("Host");
				int port = 0;
				getClientChannel(host, port);
				if (req.method.equalsIgnoreCase("Connect"))
				{
					client.addListener(new ChannelFutureListener()
					{
						@Override
						public void operationComplete(ChannelFuture future)
						        throws Exception
						{
							if (future.isSuccess())
							{

							}
							else
							{

							}
						}
					});
				}
				else
				{

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
		if (host.equals(remoteHost) && null != client)
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
		remoteHost = host;
		return client;
	}

	private int sessionId;
	private String user;
	private String remoteHost;
	private ChannelFuture client = null;
}
