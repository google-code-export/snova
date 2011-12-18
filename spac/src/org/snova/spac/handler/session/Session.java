package org.snova.spac.handler.session;

import org.arch.event.Event;
import org.arch.event.EventHeader;

public abstract class Session
{
	protected String name;
	protected int ID;
	
	public int getID()
    {
    	return ID;
    }
	public void setID(int iD)
    {
    	ID = iD;
    }
	public String getName()
    {
    	return name;
    }
	public void setName(String name)
    {
    	this.name = name;
    }
	public abstract SessionType getType();
	public abstract void handleEvent(EventHeader header,Event event);
}
