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
	// public static final int EVENT_REST_REQEUST_TYPE = 11500;
	public static final int EVENT_USER_LOGIN_TYPE = 12002;
	public static final int EVENT_TCP_CONNECTION_TYPE = 12000;
	public static final int EVENT_TCP_CHUNK_TYPE = 12001;
	public static final int EVENT_RSOCKET_ACCEPTED_TYPE = 11506;

	public static final String USER_TOKEN_HEADER = "UserToken";
	public static final String FETCHER_INDEX = "FetcherIndex";
	public static final String LOCAL_RSERVER_ADDR_HEADER = "RServerAddress";
}
