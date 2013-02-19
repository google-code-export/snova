/**
 * 
 */
package org.snova.c4.server.io;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.server.session.v3.RemoteProxySessionManager;

/**
 * @author yinqiwen
 * 
 */
public abstract class Puller
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	public static boolean asyncSupported = false;
	protected static Map<String, Map<Integer, Puller>> pullers = new ConcurrentHashMap<String, Map<Integer, Puller>>();
	protected String userToken;
	protected int index;

	private static void init(HttpServletRequest req, HttpServletResponse resp,
	        String user, int index, long timeout)
	{
		try
		{
			Class clazz = Class.forName("org.snova.c4.server.io.AsyncPuller");
			Constructor c = clazz.getConstructor(HttpServletRequest.class, Long.class);
			setPuller(user, index, (Puller) c.newInstance(req, timeout));
			asyncSupported = true;
		}
		catch (Exception e)
		{
			asyncSupported = false;
			setPuller(user, index, new SyncPuller(resp, timeout));
		}
	}

	protected static void setPuller(String user, int groupIdx, Puller p)
	{
		Map<Integer, Puller> tmp = pullers.get(user);
		if (null == tmp)
		{
			tmp = new ConcurrentHashMap<Integer, Puller>();
			pullers.put(user, tmp);
		}
		tmp.put(groupIdx, p);
	}

	protected static Puller getPuller(String user, int groupIdx)
	{
		Map<Integer, Puller> tmp = pullers.get(user);
		if (null == tmp)
		{
			tmp = new ConcurrentHashMap<Integer, Puller>();
			pullers.put(user, tmp);
			return null;
		}
		return tmp.get(groupIdx);
	}

	protected static Puller removePuller(String user, int groupIdx)
	{
		Map<Integer, Puller> tmp = pullers.get(user);
		if (null == tmp)
		{
			tmp = new ConcurrentHashMap<Integer, Puller>();
			pullers.put(user, tmp);
			return null;
		}
		return tmp.remove(groupIdx);
	}

	protected abstract void doWork(LinkedList<Event> evs) throws IOException;

	public static boolean consume(String user, int groupIdx,
	        LinkedList<Event> evs)
	{
		Puller p = getPuller(user, groupIdx);
		if (null == p)
		{
			return false;
		}
		try
        {
	        p.doWork(evs);
        }
        catch (IOException e)
        {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        } 
		return true;
	}

	public static void execute(HttpServletRequest req, HttpServletResponse resp)
	        throws IOException
	{
		String userToken = req.getHeader("UserToken");
		if (null == userToken)
		{
			userToken = "";
		}
		String miscInfo = req.getHeader("C4MiscInfo");
		String[] misc = miscInfo.split("_");
		int index = Integer.parseInt(misc[0]);
		RemoteProxySessionManager.getInstance()
		        .resumeSessions(userToken, index);
		long timeout = Integer.parseInt(misc[1]);
		timeout = timeout * 1000;
		init(req, resp, userToken, index, timeout);

		try
		{
			int bodylen = req.getContentLength();
			if (bodylen > 0)
			{
				Buffer content = new Buffer(bodylen);
				int len = 0;
				while (len < bodylen)
				{
					content.read(req.getInputStream());
					len = content.readableBytes();
				}
				if (len > 0)
				{
					RemoteProxySessionManager.getInstance().dispatchEvent(
					        userToken, index, content);
				}
			}
			resp.setStatus(200);
			resp.setContentType("image/jpeg");
			resp.setHeader("C4LenHeader", "1");
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
			return;
		}

		Puller puller = getPuller(userToken, index);
		puller.userToken = userToken;
		puller.index = index;
		puller.doWork(null);
	}

	protected void flushContent(ServletResponse resp, Buffer buf)
	        throws Exception
	{
		Buffer len = new Buffer(4);
		BufferHelper.writeFixInt32(len, buf.readableBytes(), true);
		resp.getOutputStream().write(len.getRawBuffer(), len.getReadIndex(),
		        len.readableBytes());
		resp.getOutputStream().write(buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
		resp.getOutputStream().flush();
	}
}
