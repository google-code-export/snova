import static org.junit.Assert.*;

import org.arch.buffer.Buffer;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.junit.Test;
import org.snova.heroku.common.event.EventRestRequest;


public class EventTest
{
	
	@Test
	public void test()
	{
		HTTPConnectionEvent conn = new HTTPConnectionEvent(HTTPConnectionEvent.CLOSED);
		Buffer buf2 = new Buffer(256); 
		conn.encode(buf2);
		System.out.println(buf2.readableBytes());
		EventRestRequest req = new EventRestRequest();
		EncryptEventV2 enc = new EncryptEventV2(EncryptType.SE1, conn);
		Buffer buf = new Buffer(256);
		boolean ret = enc.encode(buf);
		assertEquals(ret, true);
		
		System.out.println(buf.readableBytes());
	}
	
}
