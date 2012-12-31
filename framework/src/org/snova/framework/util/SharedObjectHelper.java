/**
 * This file is part of the hyk-proxy-framework project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: Misc.java 
 *
 * @author yinqiwen [ 2010-8-17 | 11:36:30 AM ]
 *
 */
package org.snova.framework.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.snova.framework.trace.Trace;

/**
 *
 */
public class SharedObjectHelper
{
	private static ExecutorService globalThreadPool;
	private static ClientBootstrap clientBootstrap;

	public static ClientBootstrap getClientBootstrap()
	{
		if (null == clientBootstrap)
		{
			clientBootstrap = new ClientBootstrap(
			        new NioClientSocketChannelFactory(
			                Executors.newCachedThreadPool(),
			                Executors.newCachedThreadPool()));
			clientBootstrap.setPipelineFactory(new ChannelPipelineFactory()
			{

				@Override
				public ChannelPipeline getPipeline() throws Exception
				{
					ChannelPipeline p = Channels.pipeline();
					p.addLast("empty", new SimpleChannelUpstreamHandler());
					return p;
				}
			});
		}
		return clientBootstrap;
	}

	private static ScheduledExecutorService globalTimer;

	public static ScheduledExecutorService getGlobalTimer()
	{
		if (null == globalTimer)
		{
			globalTimer = new ScheduledThreadPoolExecutor(10);
		}
		return globalTimer;
	}

	public static void setGlobalTimer(ScheduledExecutorService globalTimer)
	{
		if (null != SharedObjectHelper.globalTimer)
		{
			SharedObjectHelper.globalTimer.shutdown();
			SharedObjectHelper.globalTimer = null;
		}
		SharedObjectHelper.globalTimer = globalTimer;
	}

	private static Trace trace;

	public static Trace getTrace()
	{
		return trace;
	}

	public static void setTrace(Trace trace)
	{
		SharedObjectHelper.trace = trace;
	}

	public static ExecutorService getGlobalThreadPool()
	{
		if (null == globalThreadPool)
		{
			globalThreadPool = Executors.newCachedThreadPool();
		}
		return globalThreadPool;
	}

	public static void setGlobalThreadPool(ExecutorService globalThreadPool)
	{
		if (null != SharedObjectHelper.globalThreadPool)
		{
			SharedObjectHelper.globalThreadPool.shutdown();
		}
		SharedObjectHelper.globalThreadPool = globalThreadPool;
	}

}
