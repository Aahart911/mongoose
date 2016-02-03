package com.emc.mongoose.core.impl.load.builder;
// mongoose-common.jar
import com.emc.mongoose.common.concurrent.ThreadUtil;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-impl.jar
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.WSObject;
import com.emc.mongoose.core.impl.load.executor.BasicWSDataLoadExecutor;
import com.emc.mongoose.core.impl.io.conf.WSRequestConfigBase;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.builder.WSDataLoadBuilder;
import com.emc.mongoose.core.api.load.executor.WSDataLoadExecutor;
import com.emc.mongoose.core.api.io.conf.WSRequestConfig;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.RemoteException;
import java.util.NoSuchElementException;
/**
 Created by kurila on 05.05.14.
 */
public class BasicWSDataLoadBuilder<T extends WSObject, U extends WSDataLoadExecutor<T>>
extends DataLoadBuilderBase<T, U>
implements WSDataLoadBuilder<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicWSDataLoadBuilder(final BasicConfig appConfig)
	throws RemoteException {
		super(appConfig);
		setRunTimeConfig(appConfig);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected WSRequestConfig<T, ? extends Container<T>> getDefaultIOConfig() {
		return WSRequestConfigBase.getInstance();
	}
	//
	@Override
	public BasicWSDataLoadBuilder<T, U> setRunTimeConfig(final BasicConfig rtConfig)
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
	public final BasicWSDataLoadBuilder<T, U> clone()
	throws CloneNotSupportedException {
		final BasicWSDataLoadBuilder<T, U> lb = (BasicWSDataLoadBuilder<T, U>) super.clone();
		LOG.debug(Markers.MSG, "Cloning request config for {}", ioConfig.toString());
		return lb;
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		((WSRequestConfig) ioConfig).configureStorage(storageNodeAddrs);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually() {
		if(ioConfig == null) {
			throw new IllegalStateException("No I/O configuration instance available");
		}
		//
		final WSRequestConfig wsReqConf = (WSRequestConfig) ioConfig;
		final BasicConfig ctxConfig = BasicConfig.getContext();
		if(minObjSize > maxObjSize) {
			throw new IllegalStateException(
				String.format(
					"Min object size (%s) shouldn't be more than max (%s)",
					SizeUtil.formatSize(minObjSize), SizeUtil.formatSize(maxObjSize)
				)
			);
		}
		//
		final int minThreadCount = getMinIoThreadCount(
			ThreadUtil.getWorkerCount(), storageNodeAddrs.length, threadCount
		);
		//
		return (U) new BasicWSDataLoadExecutor<>(
			ctxConfig, wsReqConf, storageNodeAddrs, threadCount, minThreadCount,
			itemSrc == null ? getDefaultItemSource() : itemSrc,
			limitCount, minObjSize, maxObjSize, objSizeBias, limitRate, updatesPerItem
		);
	}
}
