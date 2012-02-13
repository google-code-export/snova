/**
 * 
 */
package org.snova.c4.server.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.snova.c4.server.session.SessionManager;
import org.snova.framework.util.SharedObjectHelper;

/**
 * @author wqy
 *
 */
public class TimeoutService
{
	private static long timeoutCheckPeriod = 5000;
	private static long timeoutBarrier = 50000;
	static class RoutineTask implements Runnable
	{
		private Map<String, Long> touchTimeTable = new ConcurrentHashMap<String, Long>();
		public void touch(String token)
    	{
			touchTimeTable.put(token, System.currentTimeMillis());
    	}
		@Override
        public void run()
        {
	        long now = System.currentTimeMillis();
	        List<String> timeoutset =new LinkedList<String>();
	        for(String key:touchTimeTable.keySet())
	        {
	        	long touch = touchTimeTable.get(key);
	        	if(now - touch >= timeoutBarrier)
	        	{
	        		timeoutset.add(key);
	        	}
	        }
	        
	        for(String k:timeoutset)
	        {
	        	touchTimeTable.remove(k);
	        	EventService.getInstance(k).releaseEvents();
	        	SessionManager.getInstance(k).clear();
	        }
	        
        }
	}
	
	private static RoutineTask task = null;
	
	private static void start()
	{
		if(null == task)
		{
			task = new RoutineTask();
			SharedObjectHelper.getGlobalTimer().scheduleAtFixedRate(task, timeoutCheckPeriod, timeoutCheckPeriod, TimeUnit.MILLISECONDS);
		}
	}
	
	public static void touch(String token)
	{
		start();
		task.touch(token);
	}
}
