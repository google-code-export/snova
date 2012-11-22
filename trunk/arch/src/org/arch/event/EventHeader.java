/**
 * 
 */
package org.arch.event;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;

/**
 * @author qiyingwang
 *
 */
public class EventHeader
{
	public static boolean ZIGZAG_ENABLE = true;
	
	public int type;
	public int version;
	public int hash;
	
	public EventHeader(){}
	
	public EventHeader(TypeVersion tv, int hash)
	{
		type = tv.type;
		version = tv.version;
		this.hash = hash;
	}
	
	public boolean encode(Buffer buffer)
	{
		if(ZIGZAG_ENABLE)
		{
			BufferHelper.writeVarInt(buffer, type);
			BufferHelper.writeVarInt(buffer, version);
			BufferHelper.writeVarInt(buffer, hash);
		}
		else
		{
			BufferHelper.writeFixInt32(buffer, type,true);
			BufferHelper.writeFixInt32(buffer, hash,true);
			BufferHelper.writeFixInt32(buffer, version,true);
		}
		return true;
	}
	
	public boolean decode(Buffer buffer)
	{
		try
        {
			if(ZIGZAG_ENABLE)
			{
				type = BufferHelper.readVarInt(buffer);
				version = BufferHelper.readVarInt(buffer);
				hash = BufferHelper.readVarInt(buffer);
			}
			else
			{
				type = BufferHelper.readFixInt32(buffer, true);
				hash = BufferHelper.readFixInt32(buffer, true);
				version = BufferHelper.readFixInt32(buffer, true);
			}
        }
        catch (Exception e)
        {
	        return false;
        }
		return true;
	}
	
	@Override
	public String toString()
	{
	    return "" + type + ":" + version + ":" + hash;
	}
}
