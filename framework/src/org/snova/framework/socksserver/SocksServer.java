/**
 * 
 */
package org.snova.framework.socksserver;

import java.util.concurrent.ExecutorService;

import org.snova.framework.config.SimpleSocketAddress;

import net.sourceforge.jsocks.socks.SocksServerSocket;
import net.sourceforge.jsocks.socks.SocksSocket;

/**
 * @author qiyingwang
 *
 */
public class SocksServer implements Runnable
{
	private SocksServerSocket server = null;

	public SocksServer(SimpleSocketAddress listenAddress,
	        final ExecutorService workerExecutor)
	{
		
	}
	
	@Override
    public void run()
    {
		while(true)
		{
			try
            {
				SocksSocket client = (SocksSocket) server.accept();
            }
            catch (Exception e)
            {
	            // TODO: handle exception
            }
			
		}
		
    }	
	

}
