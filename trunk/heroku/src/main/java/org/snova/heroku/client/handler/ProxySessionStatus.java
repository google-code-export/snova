/**
 * 
 */
package org.snova.heroku.client.handler;

/**
 * @author qiyingwang
 * 
 */
public enum ProxySessionStatus
{
	INITED,
	WAITING_CONNECT_RESPONSE, 
	WAITING_FIRST_RESPONSE, 
	PROCEEDING,
	SESSION_COMPLETED, 
}
