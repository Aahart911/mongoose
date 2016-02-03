package com.emc.mongoose.core.impl.load.builder;

import com.emc.mongoose.common.concurrent.ThreadUtil;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.WSObject;
import com.emc.mongoose.core.api.io.conf.WSRequestConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.builder.WSContainerLoadBuilder;
import com.emc.mongoose.core.api.load.executor.WSContainerLoadExecutor;
import com.emc.mongoose.core.impl.io.conf.WSRequestConfigBase;
import com.emc.mongoose.core.impl.load.executor.BasicWSContainerLoadExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.rmi.RemoteException;
import java.util.NoSuchElementException;

/**
 * Created by gusakk on 21.10.15.
 */
public class BasicWSContainerLoadBuilder<
	T extends WSObject,
	C extends Container<T>,
	U extends WSContainerLoadExecutor<T, C>
>
extends ContainerLoadBuilderBase<T, C, U>
implements WSContainerLoadBuilder<T, C, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicWSContainerLoadBuilder(final BasicConfig appConfig)
	throws RemoteException {
		super(appConfig);
		setRunTimeConfig(appConfig);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected WSRequestConfig<T, C> getDefaultIOConfig() {
		return WSRequestConfigBase.getInstance();
	}
	//
	@Override
	public BasicWSContainerLoadBuilder<T, C, U> setRunTimeConfig(final BasicConfig rtConfig)
	throws RemoteException {
		//
		super.setRunTimeConfig(rtConfig);
		//
		final String paramName = BasicConfig.KEY_STORAGE_SCHEME;
		try {
			WSRequestConfig.class.cast(ioConfig).setScheme(rtConfig.getStorageProto());
		} catch(final NoSuchElementException e) {
			LOG.error(Markers.ERR, MSG_TMPL_NOT_SPECIFIED, paramName);
		} catch(final IllegalArgumentException e) {
			LOG.error(Markers.ERR, MSG_TMPL_INVALID_VALUE, paramName, e.getMessage());
		}
		//
		return this;
	}
	//
	@Override @SuppressWarnings("CloneDoesntCallSuperClone")
	public final BasicWSContainerLoadBuilder<T, C, U> clone()
	throws CloneNotSupportedException {
		final BasicWSContainerLoadBuilder<T, C, U> lb
			= (BasicWSContainerLoadBuilder<T, C, U>) super.clone();
		LOG.debug(Markers.MSG, "Cloning request config for {}", ioConfig.toString());
		return lb;
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		//  do nothing
		//  ioConfig.configureStorage(storageNodeAddrs);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually() {
		if(ioConfig == null) {
			throw new IllegalStateException("Should specify request builder instance");
		}
		//
		final WSRequestConfig wsReqConf = WSRequestConfig.class.cast(ioConfig);
		final BasicConfig ctxConfig = BasicConfig.getContext();
		//
		final int minThreadCount = getMinIoThreadCount(
			ThreadUtil.getWorkerCount(), storageNodeAddrs.length, threadCount
		);
		//
		return (U) new BasicWSContainerLoadExecutor<>(
			ctxConfig, wsReqConf, storageNodeAddrs, threadCount, minThreadCount,
			itemSrc == null ? getDefaultItemSource() : itemSrc, limitCount, limitRate
		);
	}
}
