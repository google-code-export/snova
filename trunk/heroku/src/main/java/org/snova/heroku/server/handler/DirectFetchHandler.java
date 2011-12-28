/**
 * 
 */
package org.snova.heroku.server.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.NetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.event.SocketConnectRequestEvent;

/**
 * @author wqy
 * 
 */
public class DirectFetchHandler implements Runnable
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private ServerEventHandler handler;
	private Selector selector;
	private Map<Integer, SelectionKey> keyTable = new ConcurrentHashMap<Integer, SelectionKey>();

	private long lastTouchTime = -1;
	private long lastPingTime = System.currentTimeMillis();
	private long selectWaitTime = 2000;

	private List<RegisterTask> registQueue = new LinkedList<DirectFetchHandler.RegisterTask>();
	private List<SelectionKey> closeQueue = new LinkedList<SelectionKey>();
	
	class RegisterTask
	{
		public SocketChannel channel;
		public int ops;
		public Object[] attach;

		public void run() throws ClosedChannelException
		{
			SelectionKey key = channel.register(selector, ops);
			key.attach(attach);
			HTTPRequestEvent event = (HTTPRequestEvent) attach[0];
			keyTable.put(event.getHash(), key);
		}
	}

	public long getSelectWaitTime()
	{
		return selectWaitTime;
	}

	public DirectFetchHandler(ServerEventHandler serverEventHandler)
	{
		this.handler = serverEventHandler;
		try
		{
			this.selector = Selector.open();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		new Thread(this).start();
	}

	public long getPingTime()
	{
		return lastPingTime;
	}

	private void closeAllClient()
	{
		for (SelectionKey key : keyTable.values())
		{
			try
			{
				key.channel().close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		keyTable.clear();
		handler.clearEventQueue();
	}

	public void clientConnect(String host, int port, Object[] attach)
	        throws IOException
	{
		//if(logger.isDebugEnabled())
		System.out.println("Connect remote " + host + ":" + port);
		SocketChannel client = SocketChannel.open();
		client.configureBlocking(false);
		HTTPRequestEvent ev = (HTTPRequestEvent) attach[0];
		if (!ev.method.equalsIgnoreCase("Connect"))
		{
			attach[1] = buildSentBuffer(ev);
		}
		RegisterTask task = new RegisterTask();
		task.channel = 	client;
		task.attach = attach;
		if (client.connect(new InetSocketAddress(host, port)))
		{
			task.ops = SelectionKey.OP_READ | SelectionKey.OP_WRITE;
			//key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			clientConnected(ev);
			System.out.println("#####" + host + ":" + port + " connected.");
		}
		else
		{
			task.ops = SelectionKey.OP_CONNECT;
		}
		synchronized (registQueue)
        {
			registQueue.add(task);
			selector.wakeup();
        }
	}

	public void fetch(HTTPChunkEvent event)
	{
		SelectionKey key = keyTable.get(event.getHash());
		if (null != key)
		{
			Object[] attach = (Object[]) key.attachment();
			attach[1] = ByteBuffer.wrap(event.content);
			synchronized (this)
			{
				key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				selector.wakeup();
			}
		}
		else
		{
			logger.error("No connected connection found for chunk:"
			        + event.getHash());
		}
	}

	public void fetch(HTTPRequestEvent event)
	{
		String host = event.getHeader("Host");
		int port = event.url.startsWith("https") ? 443 : 80;
		if (host.indexOf(":") != -1)
		{
			String old = host;
			host = old.substring(0, old.indexOf(":"));
			port = Integer.parseInt(old.substring(old.indexOf(":") + 1));
		}
		if (NetworkHelper.isPrivateIP(host))
		{
			return;
		}
		SelectionKey key = keyTable.get(event.getHash());
		if (null != key)
		{
			SocketChannel channel = (SocketChannel) key.channel();
			if (channel.isConnected())
			{
				Object[] attach = (Object[]) key.attachment();
				attach[1] = buildSentBuffer(event);
				synchronized (this)
				{
					key.interestOps(SelectionKey.OP_READ
					        | SelectionKey.OP_WRITE);
					selector.wakeup();
					return;
				}
			}
			else
			{
				closeConnection(key);
			}
		}
		try
		{
			// System.out.println("Try to connect " + host+":" + port);
			clientConnect(host, port, new Object[] { event, null });

		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void clientConnected(HTTPRequestEvent event)
	{
		if (event.method.equalsIgnoreCase("Connect"))
		{
			HTTPResponseEvent res = new HTTPResponseEvent();
			res.statusCode = 200;
			res.setHash(event.getHash());
			handler.offer(res, false);
		}
	}

	private ByteBuffer buildSentBuffer(HTTPRequestEvent ev)
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
		}
		return ByteBuffer.wrap(msg.getRawBuffer(), 0, msg.readableBytes());
	}

	public void handleRegisteQueue()
	{
		synchronized (registQueue)
        {
			for(RegisterTask task:registQueue)
			{
				try
	            {
		            task.run();
	            }
	            catch (Exception e)
	            {
		            e.printStackTrace();
	            }
			}
			registQueue.clear();
        }
		
	}
	
	public void handleCloseQueue()
	{
		for(SelectionKey key:closeQueue)
		{
			try
            {
	            closeConnection(key);
            }
            catch (Exception e)
            {
	            e.printStackTrace();
            }
		}
		closeQueue.clear();
	}

	public void handleConnectionEvent(HTTPConnectionEvent ev)
	{
		if (ev.status == HTTPConnectionEvent.CLOSED)
		{
			SelectionKey key = keyTable.remove(ev.getHash());
			if (null != key)
			{
				closeQueue.add(key);
				selector.wakeup();
			}
		}

	}

	public long touch()
	{
		long now = System.currentTimeMillis();
		lastTouchTime = now;
		return now;
	}

	private void closeConnection(SelectionKey key)
	{
		try
		{
			Object[] attch = (Object[]) key.attachment();
			HTTPRequestEvent attach = (HTTPRequestEvent) attch[0];
			System.out.println("Close " + attach.getHeader("Host"));
			keyTable.remove(attach.getHash());
			HTTPConnectionEvent ev = new HTTPConnectionEvent(
			        HTTPConnectionEvent.CLOSED);
			ev.setHash(attach.getHash());
			handler.offer(ev, false);
			key.channel().close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		ByteBuffer buffer = ByteBuffer.allocateDirect(512 * 1024);
		while (true)
		{
			try
			{
				int ret = selector.select(selectWaitTime);
				if (ret > 0)
				{
					Set keys = selector.selectedKeys(); // 取得代表端通道的键集合
					Iterator it = keys.iterator();
					while (it.hasNext())
					{
						SelectionKey key = (SelectionKey) it.next();
						Object[] attch = (Object[]) key.attachment();
						HTTPRequestEvent attachEvent = (HTTPRequestEvent) attch[0];
						if (key.isReadable())
						{
							int bytes = 0;
							SocketChannel client = (SocketChannel) key
							        .channel();
							buffer.clear();
							try
							{
								bytes = client.read(buffer);
							}
							catch (Exception e)
							{
								closeConnection(key);
								logger.error("Failed to read client", e);
								continue;
							}

							if (bytes > 0)
							{
								buffer.flip();
								HTTPChunkEvent chunk = new HTTPChunkEvent();
								
								chunk.setHash(attachEvent.getHash());
								chunk.content = new byte[bytes];
								buffer.get(chunk.content);
								// key.interestOps(SelectionKey.OP_READ
								// | SelectionKey.OP_WRITE);
								handler.offer(chunk, true);
								// while (handler.readyEventNum() > 100)
								// {
								// Thread.sleep(10);
								// }
							}
							else
							{
								if(bytes < 0)
								{
									// client.register(sel, ops, att);
									closeConnection(key);
									continue;
								}
								
							}
						}
						if (key.isConnectable())
						{
							SocketChannel client = (SocketChannel) key
							        .channel();
							if (client.isConnectionPending())
							{
								try
								{
									client.finishConnect();
								}
								catch (Exception e)
								{
									// e.printStackTrace();
									logger.error("Failed to connect remote.", e);
									closeConnection(key);
									continue;
								}
							}
							clientConnected(attachEvent);
							// System.out.println("Remote connected!");
							key.interestOps(SelectionKey.OP_READ
							        | SelectionKey.OP_WRITE);
						}
						if (key.isWritable())
						{
							SocketChannel client = (SocketChannel) key
							        .channel();
							ByteBuffer src = (ByteBuffer) attch[1];

							try
							{
								if (null != src && src.hasRemaining())
								{
									client.write(src);
									if (src.hasRemaining())
									{
										key.interestOps(SelectionKey.OP_READ
										        | SelectionKey.OP_WRITE);
									}
									else
									{
										attch[1] = null;
										key.interestOps(SelectionKey.OP_READ);
									}
								}
								else
								{
									key.interestOps(SelectionKey.OP_READ);
								}
							}
							catch (Exception e)
							{
								logger.error("Failed to write client", e);
								closeConnection(key);
								continue;
							}
						}
					}
					keys.clear();
				}
				handleRegisteQueue();
				handleCloseQueue();
				synchronized (this)
				{
					long now = System.currentTimeMillis();
					lastPingTime = now;
					if (!handler.getEventQueue().isEmpty() && now - lastTouchTime > 10000)
					{
						logger.error("Too long time since last request handled.");
						closeAllClient();
						touch();
					}
					//System.out.println("keyTable size=" + keyTable.size());
					for(Integer sessionId:keyTable.keySet())
					{
						System.out.println("Rest session:" + sessionId);
					}
					while (handler.readyEventNum() > 100)
					{
						Thread.sleep(100);
					}
				}
			}
			catch (Exception e)
			{
				logger.error("failed to get ", e);
			}

		}
	}
}
