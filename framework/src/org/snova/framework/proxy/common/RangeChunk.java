/**
 * 
 */
package org.snova.framework.proxy.common;

import org.arch.buffer.Buffer;


/**
 * @author qiyingwang
 * 
 */
public class RangeChunk
{
	public RangeChunk(Buffer chunk, long pos)
	{
		this.chunk = chunk;
		this.pos = pos;
	}

	public Buffer chunk;
	public long pos;

}
