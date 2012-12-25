package org.arch.buffer;

import static org.junit.Assert.*;

import org.junit.Test;

public class BufferHelperTest
{

	@Test
	public void testWriteFixInt32()
	{
		Buffer buffer = new Buffer(4);
		BufferHelper.writeFixInt32(buffer, 256, true);
		int size = BufferHelper.readFixInt32(buffer, true);
		assertEquals(size, 256);
	}

}
