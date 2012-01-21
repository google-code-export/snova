/**
 * 
 */
package org.snova.c4.common.event;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;

/**
 * @author qiyingwang
 *
 */
public class C4RawSocketEvent 
{
	public C4RawSocketEvent(String domain, Buffer content)
    {
	    this.domain = domain;
	    this.content = content;
    }
	public String domain;
	public Buffer content;
	
	public void encode(Buffer buffer)
	{
		BufferHelper.writeFixInt32(buffer, domain.length(), true);
		buffer.write(domain.getBytes());
		BufferHelper.writeFixInt32(buffer, content.readableBytes(), true);
		buffer.write(content.getRawBuffer(), content.getReadIndex(), content.readableBytes());
	}
	
	public boolean decode(Buffer buf)
	{
		int current = buf.getReadIndex();
		if(buf.readableBytes() <=6)
		{
			return false;
		}
		int size = BufferHelper.readFixInt32(buf, true);
		if(buf.readableBytes() < size)
		{
			buf.setReadIndex(current);
			return false;
		}
		domain = new String(buf.getRawBuffer(), buf.getReadIndex(), size);
		buf.advanceReadIndex(size);
		if(buf.readableBytes() <=4)
		{
			buf.setReadIndex(current);
			return false;
		}
		size = BufferHelper.readFixInt32(buf, true);
		if(buf.readableBytes() < size)
		{
			buf.setReadIndex(current);
			return false;
		}
		byte[] b = new byte[size];
		buf.read(b);
		content = Buffer.wrapReadableContent(b);
		return true;
	}
}
