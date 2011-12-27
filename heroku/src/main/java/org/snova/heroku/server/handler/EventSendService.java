/**
 * 
 */
package org.snova.heroku.server.handler;

import org.arch.buffer.Buffer;

/**
 * @author qiyingwang
 *
 */
public interface EventSendService
{
	public int getMaxDataPackageSize();
	public void send(Buffer buf) throws Exception;
}
