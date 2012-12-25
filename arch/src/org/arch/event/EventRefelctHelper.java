/**
 * 
 */
package org.arch.event;

import java.lang.reflect.Field;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;

/**
 * @author qiyingwang
 * 
 */
public class EventRefelctHelper
{
	public static boolean encodeFileds(Event event, Buffer buf)
	{
		try
		{
			Field[] fs = event.getClass().getDeclaredFields();
			for (Field f : fs)
			{
				f.setAccessible(true);
				if (f.getClass().isPrimitive())
				{
					if (f.getClass().equals(int.class))
					{
						int v = f.getInt(event);
						BufferHelper.writeVarInt(buf, v);
					}
					else if (f.getClass().equals(long.class))
					{
						long v = f.getLong(event);
						BufferHelper.writeVarLong(buf, v);
					}
					else if (f.getClass().equals(short.class))
					{
						short v = f.getShort(event);
						BufferHelper.writeVarShort(buf, v);
					}
					else if (f.getClass().equals(boolean.class))
					{
						boolean v = f.getBoolean(event);
						BufferHelper.writeBoolean(buf, v);
					}
					else
					{
						return false;
					}
				}
				else
				{
					return false;
				}
			}
			return true;
		}
		catch (Exception e)
		{
			return false;
		}

	}

	public static boolean decodeFileds(Event event, Buffer buf)
	{
		try
		{
			Field[] fs = event.getClass().getDeclaredFields();
			for (Field f : fs)
			{
				f.setAccessible(true);
				if (f.getClass().isPrimitive())
				{
					if (f.getClass().equals(int.class))
					{
						int v = BufferHelper.readVarInt(buf);
						f.setInt(event, v);
					}
					else if (f.getClass().equals(long.class))
					{
						long v = BufferHelper.readVarLong(buf);
						f.setLong(event, v);
					}
					else if (f.getClass().equals(short.class))
					{
						short v = BufferHelper.readVarShort(buf);
						f.setShort(event, v);
					}
					else if (f.getClass().equals(boolean.class))
					{
						boolean v = BufferHelper.readBoolean(buf);
						f.setBoolean(event, v);
					}
					else
					{
						return false;
					}
				}
				else
				{
					return false;
				}
			}
			return true;
		}
		catch (Exception e)
		{
			return false;
		}

	}
}
