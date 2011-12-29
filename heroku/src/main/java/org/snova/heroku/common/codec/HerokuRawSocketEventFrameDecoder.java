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
	private Buffer cumulation;

	private HerokuRawSocketEvent doDecode(Buffer buffer)
	{
		HerokuRawSocketEvent ev = new HerokuRawSocketEvent(null, null);
		if(ev.decode(buffer))
		{
			return ev;
		}
		return null;
	}

	public HerokuRawSocketEvent decode(Buffer buffer)
	{
		if (null != cumulation && cumulation.readable())
		{
			cumulation.discardReadedBytes();
			cumulation.write(buffer, buffer.readableBytes());
			return doDecode(buffer);
		}
		else
		{
			HerokuRawSocketEvent ev = doDecode(buffer);
			if (null == ev)
			{
				if (buffer.readable())
				{
					if (null == cumulation)
					{
						cumulation = new Buffer(buffer.readableBytes() + 100);
					}
					cumulation.write(buffer, buffer.readableBytes());
				}			
			}
			return ev;
		}
	}
}
