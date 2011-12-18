package org.snova.spac.handler.session;

public class DirectSession extends ForwardSession
{
	public DirectSession(String addr)
    {
	    super(addr);
	    // TODO Auto-generated constructor stub
    }

	@Override
    public SessionType getType()
    {
	    // TODO Auto-generated method stub
	    return SessionType.FORWARD;
    }
}
