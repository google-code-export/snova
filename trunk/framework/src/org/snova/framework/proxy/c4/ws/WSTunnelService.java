/**
 * 
 */
package org.snova.framework.proxy.c4.ws;

import java.util.Map;

import org.arch.buffer.Buffer;
import org.arch.buffer.BufferHelper;
import org.arch.config.IniProperties;
import org.arch.event.Event;
import org.arch.event.misc.EncryptEventV2;
import org.arch.event.misc.EncryptType;
import org.jboss.netty.util.internal.ConcurrentHashMap;
import org.snova.framework.config.SnovaConfiguration;
import org.snova.framework.proxy.c4.C4ServerAuth;

/**
 * @author yinqiwen
 * 
 */
public class WSTunnelService
{
	static Map<String, WSTunnelService> tunnelServices = new ConcurrentHashMap<String, WSTunnelService>();

	C4ServerAuth server;
	IOWorker[] workers;

	public static WSTunnelService getWSTunnelService(C4ServerAuth server)
	{
		if (!tunnelServices.containsKey(server.url.toString()))
		{
			tunnelServices.put(server.url.toString(), new WSTunnelService(
			        server));
		}
		return tunnelServices.get(server.url.toString());
	}

	private WSTunnelService(C4ServerAuth server)
	{
		try
		{
			this.server = server;
			IniProperties cfg = SnovaConfiguration.getInstance()
			        .getIniProperties();
			int maxConn = cfg.getIntProperty("C4", "MaxConn", 5);
			workers = new IOWorker[maxConn];
			for (int i = 0; i < workers.length; i++)
			{
				workers[i] = new IOWorker(this, i);
				workers[i].start();
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
		int index = ev.getHash() % workers.length;
		EncryptEventV2 encrypt = new EncryptEventV2();
		encrypt.type = EncryptType.SE1;
		encrypt.ev = ev;
		encrypt.setHash(ev.getHash());
		Buffer buf = new Buffer(256);
		BufferHelper.writeFixInt32(buf, 1, true);
		encrypt.encode(buf);
		int tmp = buf.getWriteIndex();
		int len = tmp - 4;
		buf.setWriteIndex(0);
		BufferHelper.writeFixInt32(buf, len, true);
		buf.setWriteIndex(tmp);
		workers[index].write(buf);
	}

}
