/**
 * 
 */
package org.snova.heroku.server.servelet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.event.EventRestNotify;
import org.snova.heroku.server.handler.ServerEventHandler;

/**
 * @author wqy
 * 
 */
public class HTTPEventDispatcherServlet extends HttpServlet
{
	protected Logger logger = LoggerFactory.getLogger(getClass());
	ServerEventHandler handler;

	public HTTPEventDispatcherServlet(ServerEventHandler handler)
	{
		this.handler = handler;
	}

	private void send(HttpServletResponse resp, Buffer buf) throws Exception
	{
		// resp.setBufferSize(buf.readableBytes() + 100);
		resp.setStatus(200);
		resp.setContentType("image/jpeg");
		// resp.setHeader("Cache-Control", "no-cache");
		resp.setContentLength(buf.readableBytes());
		resp.getOutputStream().write(buf.getRawBuffer(), buf.getReadIndex(),
		        buf.readableBytes());
		// resp.getOutputStream().flush();
	}

	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		try
		{
			int bodylen = req.getContentLength();
			if (bodylen > 0)
			{
				Buffer content = new Buffer(bodylen);
				int len = content.read(req.getInputStream());
				if (len > 0)
				{

					while (content.readable())
					{
						Event event = EventDispatcher.getSingletonInstance()
						        .parse(content);
						//event.setAttachment(new Object[] { sendService });
						EventDispatcher.getSingletonInstance().dispatch(event);
					}
					int evcount = 0;
					Buffer buf = new Buffer(4096);
					List<Event> sentEvent = new LinkedList<Event>();
					LinkedList<Event> responseQueue = handler.getEventQueue();
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
							evcount++;
						}
						ev.encode(buf);
						sentEvent.add(ev);
					}
					while (true);
					EventRestNotify notify = new EventRestNotify();
					notify.rest = responseQueue.size();
					notify.encode(buf);
					int size = buf.readableBytes();
					try
					{
						send(resp, buf);
						//logger.info("#####Notify " + evcount + " " + size);
					}
					catch (Exception e)
					{
						for (Event ev : sentEvent)
						{
							handler.offer(ev, false);
						}
						logger.error("Requeue events since write " + size
						        + " bytes while exception occured.", e);
					}
				}
			}
		}
		catch (Throwable e)
		{
			resp.setStatus(400);
			e.printStackTrace();
			e.printStackTrace(new PrintStream(resp.getOutputStream()));
			// logger.warn("Failed to process message", e);
		}
	}
}
