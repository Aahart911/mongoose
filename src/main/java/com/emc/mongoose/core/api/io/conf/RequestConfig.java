package com.emc.mongoose.core.api.io.conf;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.container.Container;
import com.emc.mongoose.core.api.data.DataItem;
// mongoose-common.jar
import com.emc.mongoose.common.conf.RunTimeConfig;
//
import java.io.Closeable;
/**
 Created by kurila on 29.09.14.
 Shared request configuration.
 */
public interface RequestConfig<T extends DataItem, C extends Container<T>>
extends IOConfig<T, C>, Closeable {
	//
	int REQUEST_NO_PAYLOAD_TIMEOUT_SEC = 100,
		REQUEST_WITH_PAYLOAD_TIMEOUT_SEC = 100000;
	//
	String
		HOST_PORT_SEP = ":",
		PACKAGE_IMPL_BASE = "com.emc.mongoose.storage.adapter";
	//
	@Override
	RequestConfig<T, C> clone()
	throws CloneNotSupportedException;
	//
	String getAPI();
	RequestConfig<T, C> setAPI(final String api);
	//
	String getScheme();
	RequestConfig<T, C> setScheme(final String scheme);
	//
	int getPort();
	RequestConfig<T, C> setPort(final int port);
	//
	String getUserName();
	RequestConfig<T, C> setUserName(final String userName);
	//
	String getSecret();
	RequestConfig<T, C> setSecret(final String secret);
	//
	@Override
	RequestConfig<T, C> setProperties(final RunTimeConfig props);
	//
	void configureStorage(final String storageAddrs[])
	throws IllegalStateException;
}