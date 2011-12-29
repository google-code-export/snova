/**
 * 
 */
package org.snova.heroku.server.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.NetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.event.EventRestNotify;
import org.snova.heroku.common.event.HerokuRawSocketEvent;
import org.snova.heroku.server.codec.HerokuRawSocketEventFrameDecoder;

/**
 * @author wqy
 * 
 */
public class DirectFetchHandlerV2 implements Runnable
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	private ServerEventHandler handler;
	private Selector selector;
	private Map<Integer, SelectionKey> keyTable = new ConcurrentHashMap<Integer, SelectionKey>();

	private long lastTouchTime = -1;
	private long lastPingTime = System.currentTimeMillis();
	private long selectWaitTime = 200000;

	private List<ConnectTask> registQueue = new LinkedList<DirectFetchHandlerV2.ConnectTask>();
	private List<SelectionKey> closeQueue = new LinkedList<SelectionKey>();
	private SelectionKey localChannelKey = null;
	private HerokuRawSocketEventFrameDecoder decoder = null;

	class RemoteChannelAttachment
	{
		HTTPRequestEvent ev;
		ByteBuffer writeBuffer;
		boolean receivedData;
	}

	class LocalChannelAttachment
	{
		String domain;
		String localHost;
		int localPort;
		boolean isConnected;
		Buffer writeBuffer = new Buffer(0);
	}

	class ConnectTask
	{
		public InetSocketAddress remote;
		//public int ops;
		public Object attach;

		public void run() throws IOException
		{
			SocketChannel channel = SocketChannel.open();
			channel.configureBlocking(false);
			SelectionKey key = null;
			boolean connected = false;
			if(channel.connect(remote))
			{
				connected = true;
				key = channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
			}
			else
			{
				key = channel.register(selector, SelectionKey.OP_CONNECT);
			}
			key.attach(attach);
			if (attach instanceof RemoteChannelAttachment)
			{
				RemoteChannelAttachment r = (RemoteChannelAttachment) attach;
				HTTPRequestEvent event = r.ev;
				keyTable.put(event.getHash(), key);
				if(connected)
				{
					clientConnected(r.ev);
				}
			}
			else
			{
				decoder = new HerokuRawSocketEventFrameDecoder();
				localChannelKey = key;
				if(connected)
				{
					localClientConnected(key);
				}
			}
		}
	}

	public long getSelectWaitTime()
	{
		return selectWaitTime;
	}

	public DirectFetchHandlerV2(ServerEventHandler serverEventHandler)
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

	public void clientConnect(String host, int port, Object attach)
	        throws IOException
	{
		if (attach instanceof RemoteChannelAttachment)
		{
			RemoteChannelAttachment r = (RemoteChannelAttachment) attach;
			HTTPRequestEvent ev = r.ev;
			System.out.println("Connect remote " + host + ":" + port
			        + " for session:" + ev.getHash());
			if (!ev.method.equalsIgnoreCase("Connect"))
			{
				r.writeBuffer = buildSentBuffer(ev);
			}
		}

		ConnectTask task = new ConnectTask();
		task.attach = attach;
		task.remote = new InetSocketAddress(host, port);
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
			RemoteChannelAttachment attach = (RemoteChannelAttachment) key
			        .attachment();
			attach.writeBuffer = ByteBuffer.wrap(event.content);
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
		System.out.println("#####Handle HTTPRequestEvent");
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
				RemoteChannelAttachment attach = (RemoteChannelAttachment) key
				        .attachment();
				attach.writeBuffer = buildSentBuffer(event);
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
			RemoteChannelAttachment attach = new RemoteChannelAttachment();
			attach.ev = event;
			// System.out.println("Try to connect " + host+":" + port);
			clientConnect(host, port, attach);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void clientConnected(HTTPRequestEvent event)
	{
		System.out.println("######Remote connected!");
		if (event.method.equalsIgnoreCase("Connect"))
		{
			HTTPResponseEvent res = new HTTPResponseEvent();
			res.statusCode = 200;
			res.setHash(event.getHash());
			handler.offer(res, false);
		}
	}
	
	private void localClientConnected(SelectionKey key)
	{
		LocalChannelAttachment attach = (LocalChannelAttachment) key.attachment();
		attach.isConnected = true;
		Buffer content = new Buffer(32);
		EventRestNotify notify = new EventRestNotify();
		notify.encode(content);
		HerokuRawSocketEvent ev = new HerokuRawSocketEvent(attach.domain, content);
		Buffer buffer = new Buffer(256);
		ev.encode(buffer);
		if(!attach.writeBuffer.readable())
		{
			attach.writeBuffer = buffer;
		}
		else
		{
			attach.writeBuffer.discardReadedBytes();
			attach.writeBuffer.write(buffer, buffer.readableBytes());
		}
		key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		System.out.println("######Local connected!");
	}

	private ByteBuffer buildSentBuffer(HTTPRequestEvent ev)
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append(ev.method).append(" ").append(ev.url).append(" ")
		        .append("HTTP/1.1\r\n");
		for (KeyValuePair<String, String> header : ev.headers)
		{
			buffer.append(header.getName()).append(": ")
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

	private void handleConnectQueue()
	{
		synchronized (registQueue)
		{
			for (ConnectTask task : registQueue)
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
		synchronized (closeQueue)
		{
			for (SelectionKey key : closeQueue)
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

	}

	private void handleWriteQueue()
	{
		if(null != localChannelKey)
		{
			Buffer buf = new Buffer(4096);
			List<Event> sentEvent = new LinkedList<Event>();
			LinkedList<Event> responseQueue = handler.getEventQueue();
			boolean haveData = false;
			do
			{
				if (buf.readableBytes() >= 1024 * 1024)
				{
					break;
				}
				Event ev = null;
				synchronized (responseQueue)
				{
					if (responseQueue.isEmpty())
					{
						break;
					}
					ev = responseQueue.removeFirst();
				}
				ev.encode(buf);
				sentEvent.add(ev);
				haveData = true;
			}
			while (true);
			if(haveData)
			{
				
				LocalChannelAttachment attach = (LocalChannelAttachment) localChannelKey.attachment();
				if(!attach.writeBuffer.readable())
				{
					attach.writeBuffer = buf;
				}
				else
				{
					attach.writeBuffer.discardReadedBytes();
					attach.writeBuffer.write(buf, buf.readableBytes());
				}
				
				localChannelKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
				//localConnectedChannel.write(ByteBuffer.wrap(buf.getRawBuffer(), offset, length))
			}
		}
	}
	
	public void handleConnectionEvent(HTTPConnectionEvent ev)
	{
		if (ev.status == HTTPConnectionEvent.CLOSED)
		{
			SelectionKey key = keyTable.remove(ev.getHash());
			if (null != key)
			{
				synchronized (closeQueue)
				{
					closeQueue.add(key);
				}
				selector.wakeup();
			}
		}
	}

	public void handleHerokuAuth(String auth)
	{
		if(null != localChannelKey)
		{
			return;
		}
		String[] ss = auth.split("-");
		String domain = ss[0];
		String host = ss[1];
		String port = ss[2];
		try
		{
			LocalChannelAttachment attach = new LocalChannelAttachment();
			attach.domain = domain;
			attach.localHost = host;
			attach.localPort = Integer.parseInt(port);
			clientConnect(host, Integer.parseInt(port), attach);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			Object attch = key.attachment();
			key.channel().close();
			if (attch instanceof RemoteChannelAttachment)
			{
				RemoteChannelAttachment r = (RemoteChannelAttachment) attch;
				HTTPRequestEvent event = r.ev;
				System.out.println("Close " + event.getHeader("Host")
				        + " for session:" + event.getHash()
				        + ", while received data?" + r.receivedData);
				keyTable.remove(event.getHash());
				HTTPConnectionEvent ev = new HTTPConnectionEvent(
				        HTTPConnectionEvent.CLOSED);
				ev.setHash(event.getHash());
				handler.offer(ev, true);
			}
			else
			{
				closeAllClient();
			}
			if(key == localChannelKey)
			{
				localChannelKey = null;
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
		while (true)
		{
			try
			{
				int ret = selector.select(selectWaitTime);
				if (ret > 0)
				{
					Set keys = selector.selectedKeys();
					Iterator it = keys.iterator();
					while (it.hasNext())
					{
						SelectionKey key = (SelectionKey) it.next();
						Object attch = key.attachment();
						boolean isRemoteChannel = attch instanceof RemoteChannelAttachment;
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
								if (isRemoteChannel)
								{
									RemoteChannelAttachment r = (RemoteChannelAttachment) attch;
									HTTPChunkEvent chunk = new HTTPChunkEvent();
									chunk.setHash(r.ev.getHash());
									chunk.content = new byte[bytes];
									buffer.get(chunk.content);
									handler.offer(chunk, true);
									r.receivedData = Boolean.TRUE;
								}
								else
								{
									int size = buffer.remaining();
									Buffer tmp = new Buffer(size);
									buffer.get(tmp.getRawBuffer(), 0, size);
									tmp.advanceWriteIndex(size);
									System.out.println("####Handle read data:" + size);
									while(tmp.readable())
									{
										HerokuRawSocketEvent ev = decoder.decode(tmp);
										if(null == ev)
										{
											break;
										}
										System.out.println("####Handle HerokuRawSocketEvent");
										while(ev.content.readable())
										{
											Event tmpev = EventDispatcher.getSingletonInstance().parse(ev.content);
											System.out.println("####Handle event:" + tmpev.getClass().getName());
											EventDispatcher.getSingletonInstance().dispatch(tmpev);
										}
									}
								}
							}
							else
							{
								if (bytes < 0)
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
									if (!client.finishConnect())
									{
										closeConnection(key);
										continue;
									}
								}
								catch (Exception e)
								{
									// e.printStackTrace();
									logger.error("Failed to connect remote.", e);
									closeConnection(key);
									continue;
								}
							}
							
							if (isRemoteChannel)
							{
								RemoteChannelAttachment r = (RemoteChannelAttachment) attch;
								clientConnected(r.ev);
								key.interestOps(SelectionKey.OP_READ
								        | SelectionKey.OP_WRITE);
							}
							else
							{
								localClientConnected(key);
								//key.interestOps(SelectionKey.OP_READ);
							}
							continue;
						}
						if (key.isWritable())
						{	
							SocketChannel client = (SocketChannel) key
							        .channel();
							try
							{
								ByteBuffer src = null;
								if (isRemoteChannel)
								{
									RemoteChannelAttachment r = (RemoteChannelAttachment) attch;
									src = r.writeBuffer;
								}
								else
								{
									LocalChannelAttachment ac = (LocalChannelAttachment) attch;
									src = ByteBuffer.wrap(ac.writeBuffer.getRawBuffer(), ac.writeBuffer.getReadIndex(), ac.writeBuffer.readableBytes());
								}
								
								if(null == src || !src.hasRemaining())
								{
									//System.out.println("####Writable" + isRemoteChannel);
									if (isRemoteChannel)
									{
										RemoteChannelAttachment r = (RemoteChannelAttachment) attch;
										r.writeBuffer = null;
									}
									else
									{
										LocalChannelAttachment ac = (LocalChannelAttachment) attch;
										ac.writeBuffer.clear();
									}
									key.interestOps(SelectionKey.OP_READ);
									//client.register(selector, SelectionKey.OP_READ);
								}
								else
								{
									int expected = src.remaining();
									int len = client.write(src);
									System.out.println("Write back " + len);
									if (len <  expected)
									{
										if(!isRemoteChannel)
										{
											LocalChannelAttachment ac = (LocalChannelAttachment) attch;
											ac.writeBuffer.advanceReadIndex(len);
										}
										key.interestOps(SelectionKey.OP_READ
										        | SelectionKey.OP_WRITE);
									}
									else
									{
										if(!isRemoteChannel)
										{
											LocalChannelAttachment ac = (LocalChannelAttachment) attch;
											ac.writeBuffer.clear();
										}
										key.interestOps(SelectionKey.OP_READ);
									}
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
				
				handleCloseQueue();
				handleWriteQueue();
				handleConnectQueue();
				
				synchronized (this)
				{
					long now = System.currentTimeMillis();
					lastPingTime = now;
					if (!handler.getEventQueue().isEmpty()
					        && now - lastTouchTime > 10000)
					{
						logger.error("Too long time since last request handled.");
						closeAllClient();
						touch();
					}
					if (now - lastTouchTime > 5000)
					{
						System.out.println("============================");
						for (Integer sessionId : keyTable.keySet())
						{
							RemoteChannelAttachment attach = (RemoteChannelAttachment) keyTable
							        .get(sessionId).attachment();
							HTTPRequestEvent ev = attach.ev;
							System.out.println("Rest session:" + sessionId
							        + " with :" + ev.method + " "
							        + ev.getHeader("Host") + ", received data?"
							        + attach.receivedData);
						}
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
				System.exit(1);
			}

		}
	}
}
