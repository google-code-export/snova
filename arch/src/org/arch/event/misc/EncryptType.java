/**
 * 
 */
package org.arch.event.misc;

/**
 * @author qiyingwang
 * 
 */
public enum EncryptType
{
	NONE(0), SE1(1), RC4(2);
	
	int	value;
	
	EncryptType(int v)
	{
		this.value = v;
	}
	
	public int getValue()
	{
		return value;
	}
	
	public static EncryptType fromInt(int v)
	{
		if (v > RC4.value)
			return null;
		return values()[v];
	}
}
