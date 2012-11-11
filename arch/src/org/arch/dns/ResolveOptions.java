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
	public boolean	useTcp;
	public int	   cacheTtl;
	public int	   retry	= 3;
	public int	   timeout	= 5000;
}
