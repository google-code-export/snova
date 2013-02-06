package org.arch.encrypt;

import org.arch.buffer.Buffer;

public class RC4
{
	
	private static String	defaultKey	= "8976501f8451f03c5c4067b47882f2e5";
	
	public static void setDefaultKey(String key)
	{
		defaultKey = key;
	}
	
	public static String getDefaultKey()
	{
		return defaultKey;
	}
	
	public static byte[] decrypt(byte[] value, int off, int len)
	{
		byte[] src = new byte[len];
		System.arraycopy(value, off, src, 0, len);
		RC4.rc4(defaultKey, src);
		return src;
	}
	
	public static byte[] encrypt(byte[] value)
	{
		RC4.rc4(defaultKey, value);
		return value;
	}
	
	public static Buffer encrypt(Buffer buf)
	{
		byte[] array = buf.getRawBuffer();
		RC4.rc4(defaultKey, array);
		return Buffer.wrapReadableContent(array);
	}
	
	public static Buffer decrypt(Buffer buf)
	{
		byte[] array = buf.getRawBuffer();
		RC4.rc4(defaultKey, array);
		return Buffer.wrapReadableContent(array);
	}
	
	public static void rc4(String key, byte[] data)
	{
		int[] s = new int[256];
		int i, j = 0;
		for (i = 0; i < 256; i++)
		{
			s[i] = i;
		}
		
		for (i = 0; i < 256; i++)
		{
			j = (j + s[i] + key.codePointAt(i % key.length())) % 256;
			int x = s[i];
			s[i] = s[j];
			s[j] = x;
		}
		i = 0;
		j = 0;
		for (int y = 0; y < data.length; y++)
		{
			i = (i + 1) % 256;
			j = (j + s[i]) % 256;
			int x = s[i];
			s[i] = s[j];
			s[j] = x;
			int k = data[y];
			if (k < 0)
			{
				k += 256;
			}
			int tmp = k ^ s[(s[i] + s[j]) % 256];
			while (tmp >= 256)
			{
				tmp -= 256;
			}
			data[y] = (byte) tmp;
		}
	}
}
