/**
 * 
 */
package org.snova.c4.server.servlet.v2;

/**
 * @author wqy
 *
 */
import java.io.IOException;
import java.io.InputStream;

import org.arch.buffer.Buffer;

/**
 * 
 * @author Rick Rineholt
 */

public class ChunkedBufferReader
{
	protected long	           chunkSize	= 0l;
	protected volatile boolean	closed	    = false;
	private static final int	maxCharLong	= (Long.toHexString(Long.MAX_VALUE))
	                                                .toString().length();
	private InputStream in;

	public ChunkedBufferReader(InputStream is)
	{
		this.in = is;
	}
	
	public int read() throws IOException
	{
		byte[] d = new byte[1];
		int rc = in.read(d, 0, 1);
		
		return rc > 0 ? (d[0] & 0xFF) : rc;
	}
	
	
	public synchronized Buffer readChunk() throws IOException
	{
		if (closed)
			return null;
		if (0 == getChunked())
		{
			return new Buffer(0);
		}
		byte[] b = new byte[(int) chunkSize];
		int len = 0;
		int off = 0;
		do
		{
			len = in.read(b, off, (int) (chunkSize - off));
			if(len < 0)
			{
				break;
			}
			else
			{
				off += len;
			}
			if(off == chunkSize)
			{
				break;
			}
		}while(len > 0);
		if(off == chunkSize)
		{
			int cr = read();
			int lf = read();
			if(cr == '\r' && lf == '\n')
			{
				return Buffer.wrapReadableContent(b, 0, off);
			}
		}
		throw new IOException("Error chunk input.");
	}
	
	
	public int available() throws IOException
	{
		if (closed)
			return 0;
		int rc = (int) Math.min(chunkSize, Integer.MAX_VALUE);
		
		return Math.min(rc, in.available());
	}
	
	protected long getChunked() throws IOException
	{
		// StringBuffer buf= new StringBuffer(1024);
		byte[] buf = new byte[maxCharLong + 2];
		int bufsz = 0;
		
		chunkSize = -1L;
		int c = -1;
		
		do
		{
			c = in.read();
			if (c > -1)
			{
				if (c != '\r' && c != '\n' && c != ' ' && c != '\t')
				{
					buf[bufsz++] = ((byte) c);
				}
			}
		} while (c > -1 && (c != '\n' || bufsz == 0) && bufsz < buf.length);
		if (c < 0)
		{
			closed = true;
		}
		String sbuf = new String(buf, 0, bufsz);
		
		if (bufsz > maxCharLong)
		{
			closed = true;
			throw new IOException(
			        "Chunked input stream failed to receive valid chunk size:"
			                + sbuf);
		}
		try
		{
			chunkSize = Long.parseLong(sbuf, 16);
		}
		catch (NumberFormatException ne)
		{
			closed = true;
			throw new IOException("'" + sbuf + "' " + ne.getMessage());
		}
		if (chunkSize < 1L)
			closed = true;
		if (chunkSize != 0L && c < 0)
		{
			// If chunk size is zero try and be tolerant that there maybe no cr
			// or lf at the end.
			throw new IOException(
			        "HTTP Chunked stream closed in middle of chunk.");
		}
		if (chunkSize < 0L)
			throw new IOException("HTTP Chunk size received " + chunkSize
			        + " is less than zero.");
		return chunkSize;
	}
	
}
