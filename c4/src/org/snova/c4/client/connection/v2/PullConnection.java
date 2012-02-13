/**
 * 
 */
package org.snova.c4.client.connection.v2;

import org.arch.misc.crypto.base64.Base64;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.c4.client.config.C4ClientConfiguration;
import org.snova.c4.client.config.C4ClientConfiguration.C4ServerAuth;
import org.snova.framework.util.proxy.ProxyInfo;

/**
 * @author wqy
 * 
 */
class PullConnection extends HTTPPersistentConnection
{
	protected Logger	logger	= LoggerFactory.getLogger(getClass());
	
	PullConnection(C4ServerAuth auth, HTTPProxyConnectionV2 conn)
	{
		super(auth, conn);
	}
	
	@Override
	protected void doFinishTransaction()
	{
	}
	
	protected void onFullResponseReceived()
	{
		startPull();
	}
	
	synchronized void startPull()
	{
		if (!waitingResponse.get())
		{
			String url = "http://" + auth.domain + "/invoke/pull";
			final HttpRequest request = new DefaultHttpRequest(
			        HttpVersion.HTTP_1_1, HttpMethod.POST, url);
			request.setHeader("Host", auth.domain);
			request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
			if (null != C4ClientConfiguration.getInstance().getLocalProxy())
			{
				ProxyInfo info = C4ClientConfiguration.getInstance()
				        .getLocalProxy();
				if (null != info.user)
				{
					String userpass = info.user + ":" + info.passwd;
					String encode = Base64.encodeToString(userpass.getBytes(),
					        false);
					request.setHeader(HttpHeaders.Names.PROXY_AUTHORIZATION,
					        "Basic " + encode);
				}
			}
			request.setHeader("UserToken", getUserToken());
			request.setHeader("TransactionTime",C4ClientConfiguration.getInstance().getPullTransactionTime());
			request.setHeader(HttpHeaders.Names.USER_AGENT,
			        C4ClientConfiguration.getInstance().getUserAgent());
			request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, 0);
			ChannelFuture future = getRemoteFuture();
			if (future.getChannel().isConnected())
			{
				future.getChannel().write(request);
				if(logger.isDebugEnabled())
				{
					logger.debug("Write pull request:" + request);
				}
			}
			else
			{
				future.addListener(new ChannelFutureListener()
				{
					
					@Override
					public void operationComplete(ChannelFuture f)
					        throws Exception
					{
						if (f.isSuccess())
						{
							f.getChannel().write(request);
							if(logger.isDebugEnabled())
							{
								logger.debug("Write pull request:" + request);
							}
						}
					}
				});
			}
			
			waitingResponse.set(true);
		}
	}
}
