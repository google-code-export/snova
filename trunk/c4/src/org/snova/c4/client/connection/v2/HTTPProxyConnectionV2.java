/**
 * 
 */
package org.snova.c4.client.connection.v2;

import org.arch.buffer.Buffer;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.connection.ProxyConnection;

/**
 * @author wqy
 *
 */
public class HTTPProxyConnectionV2 extends ProxyConnection
{
	private PushConnection push;
	private PullConnection pull;
   
	public HTTPProxyConnectionV2(C4ServerAuth auth)
    {
	    super(auth);
	    pull = new PullConnection(auth, this);
	    push = new PushConnection(auth, this);
    }
	
	@Override
	protected void doClose()
	{
	    
	}

	@Override
    protected boolean doSend(Buffer msgbuffer)
    {
		push.send(msgbuffer);
		pull.startPull();
	    return true;
    }

	@Override
    protected int getMaxDataPackageSize()
    {
	    return -1;
    }

	@Override
    public boolean isReady()
    {
		if(C4ClientConfiguration.getInstance().isClientPullEnable())
		{
			return true;
		}
	    return !push.isWaitingResponse();
    }
	
	void handleReceivedContent(Buffer content)
	{
		doRecv(content);
	}
}
