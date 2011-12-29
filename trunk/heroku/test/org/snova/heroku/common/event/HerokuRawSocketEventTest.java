package org.snova.heroku.common.event;

import static org.junit.Assert.*;

import org.arch.buffer.Buffer;
import org.junit.Test;

public class HerokuRawSocketEventTest
{

	@Test
	public void testEncode()
	{
		Buffer buf = new Buffer();
		buf.write("xyzasf".getBytes());
		HerokuRawSocketEvent ev = new HerokuRawSocketEvent("127.0.0.1", buf);
		Buffer encode = new Buffer();
		ev.encode(encode);
		
		HerokuRawSocketEvent x = new HerokuRawSocketEvent(null, null);
		x.decode(encode);
		System.out.println(x.domain);
		System.out.println(new String(x.content.toArray()));
	}

}
