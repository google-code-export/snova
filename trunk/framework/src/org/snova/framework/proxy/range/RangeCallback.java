/**
 * 
 */
package org.snova.framework.proxy.range;

import org.arch.buffer.Buffer;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;

/**
 * @author wqy
 * 
 */
public interface RangeCallback
{
	void onHttpResponse(HTTPResponseEvent res);
	
	void onRangeChunk(Buffer buf);
	
	void writeHttpReq(HTTPRequestEvent req);
}
