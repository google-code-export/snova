/**
 * 
 */
package org.arch.dns;

/**
 * @author wqy
 * 
 */
public class ResolveOptions
{
	public static final int	DNS_CACHE_TTL_SELF	  = -2;
	public static final int	DNS_CACHE_TTL_FOREVER	= -1;
	
	public boolean	        useTcp;
	public int	            cacheTtl;
	public int	            retry	              = 3;
	public int	            timeout	              = 5000;
}
