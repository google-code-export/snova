/**
 * 
 */
package org.snova.c4.common;

/**
 * @author wqy
 *
 */
public interface C4Constants
{
	public static final int EVENT_REST_REQEUST_TYPE = 11500;
	public static final int EVENT_REST_NOTIFY_TYPE = 11501;
	public static final int EVENT_SOCKET_CONNECT_REQ_TYPE = 11502;
	public static final int EVENT_SOCKET_CONNECT_RES_TYPE = 11503;
	public static final int EVENT_SEQUNCEIAL_CHUNK_TYPE = 11504;
	public static final int EVENT_TRANSACTION_COMPLETE_TYPE = 11505;
	public static final int EVENT_RSOCKET_ACCEPTED_TYPE = 11506;
	
	public static final String USER_TOKEN_HEADER = "UserToken";
	public static final String LOCAL_RSERVER_ADDR_HEADER = "RServerAddress";
}
