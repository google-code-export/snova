/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.arch.buffer.Buffer;
import org.arch.config.IniProperties;
import org.arch.encrypt.RC4;
import org.arch.misc.crypto.base64.Base64;
import org.arch.util.NetworkHelper;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.FutureCallback;
import org.snova.http.client.HttpClient;
import org.snova.http.client.HttpClientException;

/**
 * @author yinqiwen
 * 
 */
public class PushWorker  {
	protected static Logger logger = LoggerFactory.getLogger(PushWorker.class);
	boolean isReady = true;
	int waitTime = 1;
	
	HttpTunnelService serv;
	int index;

	public PushWorker(HttpTunnelService serv, int index) {
		this.serv = serv;
		this.index = index;
	}

	public void start(Buffer buf) {
		isReady = false;
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
				HttpMethod.POST, serv.server.url.toString() + "push");

		String enc = cfg.getProperty("C4", "Encrypter", "RC4");
		if (enc.equalsIgnoreCase("RC4")) {
			String key = cfg.getProperty("Misc", "RC4Key");
			RC4.setDefaultKey(key);
			byte[] tmp = RC4.encrypt(key.getBytes());
			request.setHeader("RC4Key", Base64.encodeToString(tmp, false));
		}
		request.setHeader(HttpHeaders.Names.HOST, serv.server.url.getHost());
		request.setHeader(HttpHeaders.Names.CONNECTION, "keep-alive");
		request.setHeader("UserToken", NetworkHelper.getMacAddress());
		request.setHeader("C4MiscInfo", String.format("%d_%d", index, 25));
		request.setHeader(
				HttpHeaders.Names.USER_AGENT,
				cfg.getProperty("C4", "UserAgent",
						"Mozilla/5.0 (Windows NT 6.1; WOW64; rv:15.0) Gecko/20100101 Firefox/19.0.1"));
		request.setHeader(HttpHeaders.Names.CONTENT_TYPE,
				"application/octet-stream");
		request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, buf.readableBytes());
		request.setContent(ChannelBuffers.wrappedBuffer(buf.getRawBuffer(),
				buf.getReadIndex(), buf.readableBytes()));
		HttpClient client = HttpTunnelService.httpClient;
		try {
			client.execute(request, new HttpClientFutureCallback(buf));
		} catch (HttpClientException e) {
			logger.error("Push worker got unexpected exception:",e);
			isReady = true;
		}
	}
	
	class HttpClientFutureCallback extends FutureCallback.FutureCallbackAdapter
	{
		Buffer sendBuffer = null;
		public HttpClientFutureCallback(Buffer sendBuffer) {
			this.sendBuffer = sendBuffer;
		}

		@Override
		public void onResponse(HttpResponse res) {
			if (res.getStatus().getCode() != 200) {
				logger.error("Push worker recv unexpected response:" + res.getContent().toString(
						Charset.forName("utf8")));
				SharedObjectHelper.getGlobalTimer().schedule(new Runnable() {
					@Override
					public void run() {
						start(sendBuffer);
					}
				}, waitTime, TimeUnit.SECONDS);
				waitTime *= 2;
			} else {
				sendBuffer = null;
				isReady = true;
				serv.tryWriteEvent(index);
			}
		}

		@Override
		public void onError(String error) {
			logger.error("Push worker recv unexpected error:" + error);
			SharedObjectHelper.getGlobalTimer().schedule(new Runnable() {
				public void run() {
					start(sendBuffer);
				}
			}, waitTime, TimeUnit.SECONDS);
			waitTime *= 2;
		}
	}

	
}
