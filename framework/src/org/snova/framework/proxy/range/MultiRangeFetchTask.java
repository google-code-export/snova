/**
 * 
 */
package org.snova.framework.proxy.range;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.common.http.ContentRangeHeaderValue;
import org.snova.framework.common.http.RangeHeaderValue;

/**
 * @author wqy
 * 
 */
public class MultiRangeFetchTask
{
	protected static Logger	     logger	                  = LoggerFactory
	                                                              .getLogger(MultiRangeFetchTask.class);
	public static final int	     STATE_WAIT_NORMAL_RES	  = 0;
	public static final int	     STATE_WAIT_HEAD_RES	  = 1;
	public static final int	     STATE_WAIT_RANGE_GET_RES	= 2;
	
	public int	                 fetchLimit	              = 256 * 1024;
	public int	                 fetchWorkerNum	          = 1;
	public int	                 sessionID;
	
	private int	                 rangeState	              = STATE_WAIT_NORMAL_RES;
	
	private HTTPRequestEvent	 req;
	private HTTPResponseEvent	 res;
	private RangeHeaderValue	 originRangeHader;
	private AtomicInteger	     rangeWorker	          = new AtomicInteger(0);
	private int	                 contentEnd;
	private int	                 contentBegin;
	private int	                 rangePos;
	private int	                 expectedRangePos;
	private Map<Integer, Buffer>	chunks	              = new ConcurrentHashMap<Integer, Buffer>();
	private RangeCallback	     cb;
	
	private boolean	             closed;
	
	public void setRangeState(int rangeState)
	{
		this.rangeState = rangeState;
	}
	
	public void setRangeCallback(RangeCallback cb)
	{
		this.cb = cb;
	}
	
	public boolean processAsyncResponse(HTTPResponseEvent res)
	{
		if (closed)
		{
			logger.warn(String.format("Session[%d]closed", this.sessionID));
			return false;
		}
		if (this.rangeState == STATE_WAIT_RANGE_GET_RES
		        && res.statusCode == 302)
		{
			String location = res.getHeader("Location");
			String xrange = res.getHeader("X-Range");
			if (!StringHelper.isEmptyString(location)
			        && !StringHelper.isEmptyString(xrange))
			{
				HTTPRequestEvent freq = cloneRequest(req);
				freq.url = location;
				freq.setHeader("X-Snova-HCE", "1");
				freq.setHeader("Range", xrange);
				cb.writeHttpReq(freq);
				return true;
			}
		}
		switch (rangeState)
		{
			case STATE_WAIT_NORMAL_RES:
			{
				cb.onHttpResponse(res);
				return true;
			}
			case STATE_WAIT_HEAD_RES:
			{
				if (res.statusCode != 206)
				{
					cb.onHttpResponse(res);
					return true;
				}
				this.res = res;
				String contentRangeHeader = res.getHeader("Content-Range");
				int length = res.content.readableBytes();
				if (!StringHelper.isEmptyString(contentRangeHeader))
				{
					ContentRangeHeaderValue v = new ContentRangeHeaderValue(
					        contentRangeHeader);
					length = (int) v.getInstanceLength();
				}
				if (this.contentEnd == -1)
				{
					this.contentEnd = length - 1;
				}
				if (null != originRangeHader)
				{
					res.statusCode = 206;
					res.setHeader("Content-Range", String.format(
					        "bytes %d-%d/%d", this.contentBegin,
					        this.contentEnd, length));
				}
				else
				{
					res.statusCode = 200;
					res.removeHeader("Content-Range");
				}
				res.setHeader("Content-Length", ""
				        + (this.contentEnd - this.contentBegin + 1));
				
				int n = res.content.readableBytes();
				this.expectedRangePos += n;
				this.rangePos += n;
				cb.onHttpResponse(res);
				rangeState = STATE_WAIT_RANGE_GET_RES;
				break;
			}
			case STATE_WAIT_RANGE_GET_RES:
			{
				if (res.statusCode != 206)
				{
					logger.error("Expected 206 response, but got "
					        + res.statusCode);
					return false;
				}
				rangeWorker.decrementAndGet();
				String contentRangeHeader = res.getHeader("Content-Range");
				ContentRangeHeaderValue v = new ContentRangeHeaderValue(
				        contentRangeHeader);
				logger.info(String.format("Session[%d]Recv range chunk:%s",
				        this.sessionID, contentRangeHeader));
				chunks.put((int) v.getFirstBytePos(), res.content);
				while (chunks.containsKey(expectedRangePos))
				{
					Buffer chunk = chunks.remove(expectedRangePos);
					expectedRangePos += chunk.readableBytes();
					cb.onRangeChunk(chunk);
				}
				if (expectedRangePos < this.contentEnd)
				{
					logger.info(String.format(
					        "Session[%d]Expect range chunk:%d", this.sessionID,
					        this.expectedRangePos));
				}
				else
				{
					logger.info(String.format(
					        "Session[%d]Range task finished.", this.sessionID));
				}
				break;
			}
			default:
			{
				return false;
			}
		}
		while (!this.closed
		        && this.res.statusCode < 300
		        && rangeWorker.get() < fetchWorkerNum
		        && rangePos < contentEnd
		        && (rangePos - expectedRangePos) < fetchLimit * fetchWorkerNum
		                * 2)
		{
			synchronized (this)
			{
				int begin = this.rangePos;
				int end = this.rangePos + fetchLimit - 1;
				if (end > this.contentEnd)
				{
					end = this.contentEnd;
				}
				this.rangePos = end + 1;
				rangeWorker.incrementAndGet();
				String rangeHeader = "bytes=" + begin + "-" + end;
				logger.info(String.format("Session[%d]Fetch range:%s",
				        this.sessionID, rangeHeader));
				HTTPRequestEvent freq = cloneRequest(req);
				freq.setHeader("Range", rangeHeader);
				freq.setHeader("X-Snova-HCE", "1");
				cb.writeHttpReq(freq);
			}
		}
		return true;
	}
	
	public void close()
	{
		closed = true;
		chunks.clear();
	}
	
	private boolean processRequest(HTTPRequestEvent req)
	{
		if (!req.method.equalsIgnoreCase("GET"))
		{
			return false;
		}
		this.req = req;
		String rangeHeader = req.getHeader("Range");
		this.contentEnd = -1;
		this.contentBegin = 0;
		this.rangePos = 0;
		this.expectedRangePos = 0;
		if (!StringHelper.isEmptyString(rangeHeader))
		{
			this.originRangeHader = new RangeHeaderValue(rangeHeader);
			this.contentBegin = (int) originRangeHader.getFirstBytePos();
			this.contentEnd = (int) originRangeHader.getLastBytePos();
			this.rangePos = this.contentBegin;
			this.expectedRangePos = this.rangePos;
		}
		return true;
	}
	
	private HTTPRequestEvent cloneRequest(HTTPRequestEvent req)
	{
		HTTPRequestEvent clone = new HTTPRequestEvent();
		clone.method = req.method;
		clone.url = req.url;
		clone.setHash(req.getHash());
		for (KeyValuePair<String, String> h : req.headers)
		{
			clone.addHeader(h.getName(), h.getValue());
		}
		return clone;
	}
	
	public void asyncGet(HTTPRequestEvent req, RangeCallback cb)
	{
		this.cb = cb;
		processRequest(req);
		if (null != originRangeHader)
		{
			if (this.contentEnd > 0
			        && this.contentEnd - this.contentBegin < this.fetchLimit)
			{
				this.rangeState = STATE_WAIT_NORMAL_RES;
				cb.writeHttpReq(req);
				return;
			}
		}
		
		HTTPRequestEvent freq = cloneRequest(req);
		freq.setHeader("Range", String.format("bytes=%d-%d", this.contentBegin,
		        this.contentBegin + this.fetchLimit - 1));
		freq.setHeader("X-Snova-HCE", "1");
		this.rangeState = STATE_WAIT_HEAD_RES;
		cb.writeHttpReq(freq);
	}
}
