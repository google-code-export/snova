/**
 * 
 */
package org.snova.c4.client.handler;

/**
 * @author qiyingwang
 * 
 */
public enum ProxySessionStatus
{
	INITED,
	WAITING_CONNECT_RESPONSE, 
	WAITING_RESPONSE, 
	TRANSACTION_COMPELETE,
	PROCEEDING,
	SESSION_COMPLETED, 
}
