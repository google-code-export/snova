/**
 * 
 */
package org.snova.framework.proxy.google;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpChunk;
import io.netty.handler.codec.http.HttpRequest;

import org.snova.framework.proxy.LocalProxyHandler;
import org.snova.framework.proxy.RemoteProxyHandler;

/**
 * @author wqy
 *
 */
public class GoogleRemoteHandler implements RemoteProxyHandler
{

	@Override
    public void handleRequest(LocalProxyHandler local, HttpRequest req)
    {
	    
    }

	@Override
    public void handleChunk(LocalProxyHandler local, HttpChunk chunk)
    {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void handleRawData(LocalProxyHandler local, ByteBuf raw)
    {
	    // TODO Auto-generated method stub
	    
    }

	@Override
    public void close()
    {
	    // TODO Auto-generated method stub
	    
    }
	
}
