/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: SimpleSecurityService.java 
 *
 * @author yinqiwen [ 2010-5-15 | 10:32:03 PM]
 *
 */
package org.arch.encrypt;

import java.nio.ByteBuffer;

import org.arch.buffer.Buffer;

/**
 *
 */
public class RC4Encrypt
{
	private static byte[]	key	= "8976501f8451f03c5c4067b47882f2e5".getBytes();
	private static RC4	  rc4	= new RC4(key);
	
	public static void setKey(String key)
	{
		RC4Encrypt.key = key.getBytes();
		rc4 = new RC4(RC4Encrypt.key);
	}
	
	public byte[] decrypt(byte[] value, int off, int len)
	{
		byte[] src = new byte[len];
		System.arraycopy(value, off, src, 0, len);
		return rc4.decrypt(src);
	}
	
	public Buffer encrypt(Buffer buf)
	{
		byte[] array = buf.getRawBuffer();
		return Buffer.wrapReadableContent(rc4.encrypt(array));
	}
	
	public Buffer decrypt(Buffer buf)
	{
		byte[] array = buf.getRawBuffer();
		return Buffer.wrapReadableContent(rc4.decrypt(array));
	}
	
}
