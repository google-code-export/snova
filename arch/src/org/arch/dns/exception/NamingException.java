/**
 * 
 */
package org.arch.dns.exception;


/**
 * @author yinqiwen
 * 
 */
public class NamingException extends Exception
{
	
	public NamingException()
	{
	}
	
	public NamingException(String msg)
	{
		super(msg);
	}
	
	public void setRootCause(Exception e)
	{
		// TODO Auto-generated method stub
		
	}
	
}
