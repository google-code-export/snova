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
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4ServerAuth;
import org.snova.framework.proxy.hosts.HostsService;
import org.snova.framework.util.SharedObjectHelper;
import org.snova.http.client.Connector;
import org.snova.http.client.HttpClient;
import org.snova.http.client.Options;
import org.snova.http.client.ProxyCallback;

/**
 * @author qiyingwang
 * 
 */
public class HttpTunnelService
{
	static HttpClient httpClient = null;
	static Map<String, HttpTunnelService> tunnelServices = new ConcurrentHashMap<String, HttpTunnelService>();
	C4ServerAuth server;
	private LinkedList<Event>[] sendEventQueue = new LinkedList[0];
	private PushWorker[] pusher;
	private PullWorker[] puller;

	private static void initHttpClient() throws Exception
	{
		if (null != httpClient)
		{
			return;
		}
		final IniProperties cfg = SnovaConfiguration.getInstance()
		        .getIniProperties();
		Options options = new Options();
		options.maxIdleConnsPerHost = cfg.getIntProperty("C4",
		        "ConnectionPoolSize", 5);
		String proxy = cfg.getProperty("C4", "Proxy");
		if (null != proxy)
		{
			final URL proxyUrl = new URL(proxy);
			options.proxyCB = new ProxyCallback()
			{
				@Override
				public URL getProxy(HttpRequest request)
				{
					return proxyUrl;
				}
			};
			options.connector = new Connector()
			{
				@Override
				public ChannelFuture connect(String host, int port)
				{
					String remoteHost = HostsService.getMappingHost(host);
					return SharedObjectHelper.getClientBootstrap().connect(
					        new InetSocketAddress(remoteHost, port));
				}
			};
		}
		httpClient = new HttpClient(options,
		        SharedObjectHelper.getClientBootstrap());
	}

	public static HttpTunnelService getHttpTunnelService(C4ServerAuth server)
	{
		if (!tunnelServices.containsKey(server.url.toString()))
		{
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
			sendEventQueue = new LinkedList[5];
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void write(Event ev)
	{
		int index = ev.getHash() % sendEventQueue.length;
		synchronized (sendEventQueue[index])
        {
			
			sendEventQueue[index].add(ev);
        }
		if (pusher[index].isReady)
		{
			tryWriteEvent(index);
		}
	}

	void tryWriteEvent(int index)
	{
		Buffer buffer = new Buffer(4096);
		synchronized (sendEventQueue[index])
        {
	        while(!sendEventQueue[index].isEmpty())
	        {
	        	sendEventQueue[index].removeFirst().encode(buffer);
	        	
	        }
        }
		
		pusher[index].start(buffer);
	}

}
