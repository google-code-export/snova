/**
 * This file is part of the hyk-proxy project.
 * Copyright (c) 2010 Yin QiWen <yinqiwen@gmail.com>
 *
 * Description: ListSelector.java 
 *
 * @author yinqiwen [ 2010-5-22 | 11:24:52 AM ]
 *
 */
package org.arch.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


/**
 *
 */
public class ListSelector<T>
{
	protected ArrayList<T>		list;
	protected int						cursor;

	public ListSelector()
	{
		list = new ArrayList<T>();
	}
	
	public ListSelector(List<T> list)
	{
		this(list, true);
	}
	
	public ListSelector(List<T> list, boolean shuffle)
	{
		if(shuffle)
		{
			Collections.shuffle(list);
		}
		this.list = new ArrayList<T>(list);
	}
	
	public void add(T obj)
	{
		list.add(obj);
	}
	
	public int size()
	{
		return list.size();
	}
	
	public synchronized T get(int idx)
	{
		if(list.isEmpty())
		{
			return null;
		}
		if(idx >= list.size())
		{
			return null;
		}
		return list.get(idx);
	}
	
	public synchronized T select()
	{
		if(list.isEmpty())
		{
			return null;
		}
		if(cursor >= list.size())
		{
			cursor = 0;
		}
		return list.get(cursor++);
	}
	
	public synchronized T randomSelect()
	{
		if(list.isEmpty())
		{
			return null;
		}
		if(1 == list.size())
		{
			return list.get(0);
		}
		Random random = new Random();
		return list.get(random.nextInt(list.size()));
	}
	
	public synchronized void remove(T obj)
	{
		list.remove(obj);
	}
}
