/**
 * 
 */
package org.snova.heroku.common.codec;

import org.arch.buffer.Buffer;
import org.snova.heroku.common.event.HerokuRawSocketEvent;

/**
 * @author qiyingwang
 * 
 */
public class HerokuRawSocketEventFrameDecoder
{
	private Buffer cumulation = new Buffer(0);

	private HerokuRawSocketEvent doDecode(Buffer buffer)
	{
		HerokuRawSocketEvent ev = new HerokuRawSocketEvent(null, null);
		if(ev.decode(buffer))
		{
			return ev;
		}
		return null;
	}
	
	public int cumulationSize()
	{
		return null == cumulation?0:cumulation.readableBytes();
	}

	public HerokuRawSocketEvent decode(Buffer buffer)
	{
		HerokuRawSocketEvent ev = null;
		if (null != cumulation && cumulation.readable())
		{
			cumulation.discardReadedBytes();
			cumulation.write(buffer, buffer.readableBytes());
			ev= doDecode(cumulation);
		}
		else
		{
			ev = doDecode(buffer);
		}
		if (null == ev)
		{
			if (buffer != cumulation && buffer.readable())
			{
				if (null == cumulation)
				{
					cumulation = new Buffer(buffer.readableBytes() + 100);
				}
				cumulation.discardReadedBytes();
				cumulation.write(buffer, buffer.readableBytes());
			}			
		}
		return ev;
	}
}
