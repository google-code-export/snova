package org.snova.c4.client.connection;

import java.util.List;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.c4.client.handler.ProxySessionManager;
import org.snova.c4.common.event.EventRestRequest;

public class DualHTTPProxyConnection extends ProxyConnection implements ProxyConnectionStateListner
{
	
	private HTTPProxyConnection	primay;
	private HTTPProxyConnection	assist;
	
	protected DualHTTPProxyConnection(C4ServerAuth auth)
	{
		super(auth);
		primay = new HTTPProxyConnection(auth, true);
		assist = new HTTPProxyConnection(auth, false);
		assist.setStateListener(this);
		activeAssistConnection();
	}
	
	private void activeAssistConnection()
	{
		if(primay.isReady())
		{
			send((List<Event>) null);
			assist.send(ProxySessionManager.getInstance().getEventRestRequest(this));
		}
		else
		{
			if(!assist.isReady())
			{
				assist.send(ProxySessionManager.getInstance().getEventRestRequest(this));
			}
			else
			{
				send(ProxySessionManager.getInstance().getEventRestRequest(this));
			}
			//assist.send();
		}
		
		//ProxySessionManager.getInstance().sendEventRestRequest(this, assist);
		
	}
	
	@Override
	protected boolean doSend(Buffer msgbuffer)
	{
		if(primay.isReady())
		{
			//activeAssistConnection();
			return primay.doSend(msgbuffer);
		}
		else if(assist.isReady())
		{
			return assist.doSend(msgbuffer);
		}
		//activeAssistConnection();
		return false;
	}
	
	@Override
	protected int getMaxDataPackageSize()
	{
		// TODO Auto-generated method stub
		return -1;
	}
	
	@Override
	public boolean isReady()
	{
		return primay.isReady() || assist.isReady();
	}

	@Override
    public void onAvailable()
    {
		if(logger.isDebugEnabled())
		{
			logger.debug("Resend assist request!");
		}
		activeAssistConnection();
    }
}
