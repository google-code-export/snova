/**
 * 
 */
package org.snova.framework.proxy.c4.http;

import java.net.InetSocketAddress;
import java.net.URL;
import java.util.LinkedList;
import java.util.Map;

import org.arch.buffer.Buffer;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4ServerAuth;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.Connector;
import org.snova.http.client.HttpClient;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;

/**
 * @author yinqiwen
 * 
 */
public class HttpTunnelService {
	protected static Logger logger = LoggerFactory
			.getLogger(HttpTunnelService.class);
	static HttpClient httpClient = null;
	static Map<String, HttpTunnelService> tunnelServices = new ConcurrentHashMap<String, HttpTunnelService>();
	C4ServerAuth server;
	private LinkedList<Event>[] sendEventQueue = new LinkedList[0];
	private PushWorker[] pusher;
	private PullWorker[] puller;

	private static void initHttpClient() throws Exception {
		if (null != httpClient) {
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
				.getIniProperties();
		Options options = new Options();
		options.maxIdleConnsPerHost = cfg.getIntProperty("C4",
				"ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("C4", "Proxy");
		if (null != proxy) {
			final URL proxyUrl = new URL(proxy);
			options.proxyCB = new ProxyCallback() {
				@Override
				public URL getProxy(HttpRequest request) {
					return proxyUrl;
				}
			};
			options.connector = new Connector() {
				@Override
				public ChannelFuture connect(String host, int port) {
					String remoteHost = HostsService.getMappingHost(host);
					return SharedObjectHelper.getClientBootstrap().connect(
							new InetSocketAddress(remoteHost, port));
				}
			};
		}
		httpClient = new HttpClient(options,
				SharedObjectHelper.getClientBootstrap());
	}

	public static HttpTunnelService getHttpTunnelService(C4ServerAuth server) {
		if (!tunnelServices.containsKey(server.url.toString())) {
			tunnelServices.put(server.url.toString(), new HttpTunnelService(
					server));
		}
		return tunnelServices.get(server.url.toString());
	}

	private HttpTunnelService(C4ServerAuth server)
	{
		try
		{
			this.server = server;
			initHttpClient();
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			int maxConn = cfg.getIntProperty("C4", "MaxConn", 5);
			sendEventQueue = new LinkedList[maxConn];
			for (int i = 0; i < sendEventQueue.length; i++)
			{
				sendEventQueue[i] = new LinkedList<Event>();
			}
			pusher = new PushWorker[sendEventQueue.length];
			for (int i = 0; i < sendEventQueue.length; i++)
			{
				pusher[i] = new PushWorker(this, i);
			}
			puller = new PullWorker[sendEventQueue.length];
			for (int i = 0; i < sendEventQueue.length; i++)
			{
				puller[i] = new PullWorker(this, i);
				puller[i].start();
			}
			
		}
		catch (Exception e)
		{
			logger.error("Failed to init http tunnel service.",e);
		}
	}

	public void write(Event ev) {
		int index = ev.getHash() % sendEventQueue.length;
		EncryptEventV2 encrypt = new EncryptEventV2();
		IniProperties cfg = SnovaConfiguration.getInstance().getIniProperties();
		String enc = cfg.getProperty("C4", "Encrypter", "RC4");
		if (enc.equalsIgnoreCase("RC4")) {
			encrypt.type = EncryptType.RC4;
		} else if (enc.equalsIgnoreCase("SE1")) {
			encrypt.type = EncryptType.SE1;
		} else {
			encrypt.type = EncryptType.NONE;
		}

		encrypt.ev = ev;
		encrypt.setHash(ev.getHash());
		synchronized (sendEventQueue[index]) {
			sendEventQueue[index].add(encrypt);
		}
		if (pusher[index].isReady) {
			tryWriteEvent(index);
		}
	}

	void tryWriteEvent(int index) {
		Buffer buffer = new Buffer(4096);
		synchronized (sendEventQueue[index]) {
			while (!sendEventQueue[index].isEmpty()) {
				sendEventQueue[index].removeFirst().encode(buffer);
			}
		}
		if (buffer.readable()) {
			pusher[index].start(buffer);
		}
	}
}
