/**
 * 
 */
package org.snova.heroku.server.handler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.arch.buffer.Buffer;
import org.arch.common.KeyValuePair;
import org.arch.event.http.HTTPChunkEvent;
import org.arch.event.http.HTTPConnectionEvent;
import org.arch.event.http.HTTPRequestEvent;
import org.arch.event.http.HTTPResponseEvent;
import org.arch.util.NetworkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snova.heroku.common.event.SequentialChunkEvent;

/**
 * @author wqy
 * 
 */
public interface FetchHandler
{
	public long touch();
	//public void fetch(HTTPChunkEvent event);
	public void fetch(SequentialChunkEvent event);
	public void fetch(HTTPRequestEvent event);
	public void handleConnectionEvent(HTTPConnectionEvent ev);
	public void handleHerokuAuth(String auth);
	public void verifyAlive(List<Integer> sessionIDs);
}
