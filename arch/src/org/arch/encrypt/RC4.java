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
		_RC4 rc4 = new _RC4(key.getBytes());
		rc4.encrypt(data);
	}
	
	final static class _RC4 {

	    private int _i, _j;
		private final byte[] _s = new byte[256];

		public _RC4(byte[] key) {
			int key_length = key.length;

		    for (int i = 0; i < 256; i++)
		        _s[i] = (byte)i;

		    for (int i=0, j=0; i < 256; i++) {
		        byte temp;

		        j = (j + key[i % key_length] + _s[i]) & 255;
		        temp = _s[i];
		        _s[i] = _s[j];
		        _s[j] = temp;
		    }

		    _i = 0;
		    _j = 0;
		}

		public byte output() {
		    byte temp;
		    _i = (_i + 1) & 255;
		    _j = (_j + _s[_i]) & 255;

		    temp = _s[_i];
		    _s[_i] = _s[_j];
		    _s[_j] = temp;

		    return _s[(_s[_i] + _s[_j]) & 255];
		}

		public void encrypt(byte[] in) {
			for (int i = 0; i < in.length; i++) {
				in[i] = (byte) (in[i] ^ output());
			}
		}
		public void encrypt(byte[] in, int offset, int len) {
			int end = offset+len;
			for (int i = offset; i < end; i++) {
				in[i] = (byte) (in[i] ^ output());
			}

		}
	}
}
