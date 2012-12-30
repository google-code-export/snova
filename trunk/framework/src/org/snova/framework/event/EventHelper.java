/**
 * 
 */
package org.snova.framework.event;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventDispatcher;
import org.arch.event.http.HTTPRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author yinqiwen
 * 
 */
public class EventHelper
{
	protected static Logger	logger	= LoggerFactory
	                                       .getLogger(EventHelper.class);
	
	public static void resetEncodedEvent(Event ev)
	{
		ev = Event.extractEvent(ev);
		if (ev instanceof HTTPRequestEvent)
		{
			HTTPRequestEvent req = (HTTPRequestEvent) ev;
			req.content.setReadIndex(0);
		}
	}
	
	public static Event parseEvent(Buffer buffer) throws Exception
	{
		EventHeaderTags tags = new EventHeaderTags();
		return parseEvent(buffer, tags);
	}
	
	public static Event parseEvent(Buffer buffer, EventHeaderTags tags)
	        throws Exception
	{
		// EventHeaderTags tags = new EventHeaderTags();
		if (!EventHeaderTags.readHeaderTags(buffer, tags))
		{
			logger.error("Failed to read event header tags.");
			return null;
		}
		return EventDispatcher.getSingletonInstance().parse(buffer);
	}
	
	public static Buffer encodeEvent(EventHeaderTags tags, Event event)
	{
		Buffer buf = new Buffer(256);
		tags.encode(buf);
		Buffer content = new Buffer(256);
		event.encode(content);
		buf.write(content, content.readableBytes());
		return buf;
	}
}
