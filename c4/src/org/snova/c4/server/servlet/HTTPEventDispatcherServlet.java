/**
 * 
 */
package org.snova.c4.server.servlet;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.common.event.EventRestNotify;
import org.snova.c4.server.service.EventService;
import org.snova.c4.server.session.DirectSession;
import org.snova.c4.server.session.SessionManager;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 * 
 */
public class HTTPEventDispatcherServlet extends HttpServlet
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	private static long timeoutCheckPeriod = 5000;
	static TimeoutTask task = new TimeoutTask();
    static class TimeoutTask implements Runnable
    {
    	private long lastTouchTime;
    	private boolean running = false;
    	public void touch()
    	{
    		lastTouchTime = System.currentTimeMillis();
    	}
		@Override
        public void run()
        {
	        long now = System.currentTimeMillis();
	        if(now - lastTouchTime > timeoutCheckPeriod*2)
	        {
	        	DirectSession.releaseExternalConnections();
	        	SessionManager.getInstance().clear();
	        	EventService.getInstance().releaseEvents();
	        }
        }
    }
	
	public HTTPEventDispatcherServlet()
	{
		if(!task.running)
		{
			task.running = true;
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(task, timeoutCheckPeriod, timeoutCheckPeriod, TimeUnit.MILLISECONDS);
		}
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
		resp.getOutputStream().flush();
		resp.getOutputStream().close();
	}
	
	@Override
	protected void doPost(HttpServletRequest req, final HttpServletResponse resp)
	        throws ServletException, IOException
	{
		task.touch();
		try
		{
			int bodylen = req.getContentLength();
			if (bodylen > 0)
			{
				Buffer content = new Buffer(bodylen);
				int len = 0;
				while(len < bodylen)
				{
					content.read(req.getInputStream());
					len = content.readableBytes();
				}
				if (len > 0)
				{
					EventService.getInstance().dispatchEvent(content);
					// int evcount = 0;
					Buffer buf = new Buffer(4096);
					EventService.getInstance().extractEventResponses(buf);
					
					//EventRestNotify notify = new EventRestNotify();
					//notify.rest = EventService.getInstance()
					 //       .getRestEventQueueSize();
					//notify.encode(buf);
					int size = buf.readableBytes();
					try
					{
						send(resp, buf);
					}
					catch (Exception e)
					{
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
		task.touch();
	}
}
