/**
 * This file is part of the hyk-proxy-gae project.
 * Copyright (c) 2011 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: RequestSharedAppIDEvent.java 
 *
 * @author yinqiwen [ 2011-12-7 | ÏÂÎç10:18:10 ]
 *
 */
package org.snova.framework.event.gae;

import org.arch.buffer.Buffer;
import org.arch.event.Event;
import org.arch.event.EventType;
import org.arch.event.EventVersion;
import org.snova.framework.event.CommonEventConstants;

/**
 *
 */
@EventType(CommonEventConstants.REQUEST_SHARED_APPID_EVENT_TYPE)
@EventVersion(1)
public class RequestSharedAppIDEvent extends Event
{

	@Override
    protected boolean onDecode(Buffer buffer)
    {
	    return true;
    }

	@Override
    protected boolean onEncode(Buffer buffer)
    {
		 return true;
    }

}
