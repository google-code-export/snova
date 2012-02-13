/**
 * 
 */
package org.snova.c4.client.connection;

import org.arch.buffer.Buffer;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;

/**
 * @author wqy
 *
 */
public class RSocketProxyConnection extends ProxyConnection
{
	protected RSocketProxyConnection(C4ServerAuth auth)
    {
	    super(auth);
    }

	@Override
    protected boolean doSend(Buffer msgbuffer)
    {
	    // TODO Auto-generated method stub
	    return false;
    }

	@Override
    protected int getMaxDataPackageSize()
    {
	    return -1;
    }

	@Override
    public boolean isReady()
    {
	    return true;
    }
}
