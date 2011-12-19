/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: SeattleProxyEventService.java 
 *
 * @author qiying.wang [ May 21, 2010 | 10:14:39 AM ]
 *
 */
package org.snova.spac.handler;

import org.arch.event.Event;
import org.arch.event.EventHeader;
import org.arch.event.NamedEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.spac.session.SessionManager;
import org.tykedog.csl.interpreter.CSL;

/**
 *
 */
public class SpacProxyEventHandler implements NamedEventHandler
{
	protected Logger	logger	    = LoggerFactory.getLogger(getClass());
	
	private SessionManager	 sessionManager	= new SessionManager();
	private CSL scriptEngine;

	public void setScriptEngine(CSL scriptEngine)
    {
		if(this.scriptEngine != scriptEngine)
		{
			this.scriptEngine = scriptEngine;
			sessionManager.setScriptEngine(this.scriptEngine);
		}
    }

	@Override
	public void onEvent(EventHeader header, Event event)
	{
		sessionManager.handleEvent(header, event);
	}
	
	@Override
	public String getName()
	{
		// TODO Auto-generated method stub
		return "SPAC";
	}
	
}
