/**
 * 
 */
package org.snova.c4.server.session.v3;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.TypeVersion;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.snova.c4.common.C4Constants;
import org.snova.c4.common.event.C4Events;
import org.snova.c4.common.event.UserLoginEvent;

/**
 * @author wqy
 * 
 */
public class RemoteProxySessionManager implements Runnable
{
	class RegisteTask
	{
		SocketChannel ch;
		int op;
		RemoteProxySession attach;
	}

	Selector selector;

	private Map userSessionGroup = new HashMap();
	private Map userReadyEventQueue = new HashMap();
	private LinkedList<RegisteTask> registQueue = new LinkedList<RegisteTask>();
	static RemoteProxySessionManager instance = new RemoteProxySessionManager();

	private RemoteProxySessionManager()
	{
		try
		{
			C4Events.init(null, true);
			selector = Selector.open();
			new Thread(this).start();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void processRegisteTask()
	{
		synchronized (registQueue)
		{
			while (!registQueue.isEmpty())
			{
				RegisteTask task = registQueue.removeFirst();
				try
				{
					task.attach.key = task.ch.register(selector, task.op,
					        task.attach);
				}
				catch (ClosedChannelException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public static RemoteProxySessionManager getInstance()
	{
		return instance;
	}

	private synchronized LinkedList<Event> getEventQueue(String user,
	        int groupIdx)
	{
		HashMap eqs = (HashMap) userReadyEventQueue.get(user);
		if (null == eqs)
		{
			eqs = new HashMap();
			userReadyEventQueue.put(user, eqs);
		}
		LinkedList<Event> queue = (LinkedList<Event>) eqs.get(groupIdx);
		if (null == queue)
		{
			queue = new LinkedList<Event>();
			eqs.put(groupIdx, queue);
		}
		return queue;
	}

	boolean offerReadyEvent(String user, int groupIdx, Event ev)
	{
		EncryptEventV2 encrypt = new EncryptEventV2();
		encrypt.type = EncryptType.SE1;
		encrypt.ev = ev;
		encrypt.setHash(ev.getHash());
		LinkedList<Event> queue = getEventQueue(user, groupIdx);
		synchronized (queue)
		{
			queue.add(encrypt);
			queue.notify();
			return queue.size() <= 10;
		}
	}

	public void consumeReadyEvent(String user, int groupIndex, Buffer buf,
	        long timeout)
	{
		LinkedList<Event> queue = getEventQueue(user, groupIndex);
		synchronized (queue)
		{
			if (queue.isEmpty())
			{
				try
				{
					resumeSessions(user, groupIndex);
					queue.wait(timeout);
				}
				catch (InterruptedException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (!queue.isEmpty())
			{
				queue.removeFirst().encode(buf);
			}
		}
	}

	public void resumeSessions(String user, int groupIdx)
	{
		synchronized (userSessionGroup)
		{
			HashMap sessionGroup = (HashMap) userSessionGroup.get(user);
			if (null != sessionGroup)
			{
				HashMap sessionMap = (HashMap) userSessionGroup.get(groupIdx);
				if (null != sessionMap)
				{
					for (Object key : sessionMap.keySet())
					{
						RemoteProxySession session = (RemoteProxySession) sessionMap
						        .get(key);
						session.resume();
					}
				}
			}
		}
	}

	public void pauseSessions(String user, int groupIdx)
	{
		synchronized (userSessionGroup)
		{
			HashMap sessionGroup = (HashMap) userSessionGroup.get(user);
			if (null != sessionGroup)
			{
				HashMap sessionMap = (HashMap) userSessionGroup.get(groupIdx);
				if (null != sessionMap)
				{
					for (Object key : sessionMap.keySet())
					{
						RemoteProxySession session = (RemoteProxySession) sessionMap
						        .get(key);
						session.pause();
					}
				}
			}
		}
	}

	void registeSelector(int ops, RemoteProxySession session)
	{
		synchronized (registQueue)
		{
			RegisteTask task = new RegisteTask();
			task.attach = session;
			task.ch = session.client;
			task.op = ops;
			registQueue.add(task);
		}
		selector.wakeup();
	}

	RemoteProxySession getSession(String user, int groupIdx, int sid)
	{
		synchronized (userSessionGroup)
		{
			HashMap sessionGroup = (HashMap) userSessionGroup.get(user);
			if (null == sessionGroup)
			{
				sessionGroup = new HashMap();
				userSessionGroup.put(user, sessionGroup);
			}
			HashMap sessionMap = (HashMap) userSessionGroup.get(groupIdx);
			if (null == sessionMap)
			{
				sessionMap = new HashMap();
				sessionGroup.put(user, sessionMap);
			}
			RemoteProxySession session = (RemoteProxySession) sessionMap
			        .get(sid);
			if (null == session)
			{
				session = new RemoteProxySession(this, user, groupIdx, sid);
				sessionMap.put(sid, session);
			}
			return session;
		}
	}

	private void clearUser(String user)
	{
		HashMap sessionGroup = (HashMap) userSessionGroup.remove(user);
		if (null != sessionGroup)
		{
			for (Object key : sessionGroup.keySet())
			{
				HashMap ss = (HashMap) sessionGroup.get(key);
				if (null != ss)
				{
					for (Object sid : ss.keySet())
					{
						RemoteProxySession session = (RemoteProxySession) ss
						        .get(sid);
						if (null != session)
						{
							session.close();
						}
					}
				}
			}
		}
		userReadyEventQueue.remove(user);
	}

	public void dispatchEvent(String user, int groupIdx, final Buffer content)
	        throws Exception
	{
		while (content.readable())
		{
			Event event = EventDispatcher.getSingletonInstance().parse(content);
			event = Event.extractEvent(event);
			TypeVersion tv = Event.getTypeVersion(event.getClass());
			if (tv.type == C4Constants.EVENT_USER_LOGIN_TYPE)
			{
				UserLoginEvent usev = (UserLoginEvent) event;
				clearUser(usev.user);
			}
			else
			{
				RemoteProxySession s = getSession(user, groupIdx,
				        event.getHash());
				s.handleEvent(tv, event);
			}
		}
	}

	@Override
	public void run()
	{
		while (true)
		{
			try
			{
				processRegisteTask();
				int n = selector.select();
				if (n > 0)
				{
					Iterator keys = this.selector.selectedKeys().iterator();
					while (keys.hasNext())
					{
						SelectionKey key = (SelectionKey) keys.next();
						keys.remove();
						RemoteProxySession session = (RemoteProxySession) key
						        .attachment();
						if (key.isReadable())
						{
							session.onRead();
						}
						else if (key.isConnectable())
						{
							session.onConnected();
						}
					}
				}
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
