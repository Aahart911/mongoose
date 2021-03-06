package com.emc.mongoose.core.impl.load.builder;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
import com.emc.mongoose.core.api.load.builder.HttpContainerLoadBuilder;
import com.emc.mongoose.core.api.load.executor.HttpContainerLoadExecutor;
import com.emc.mongoose.core.impl.io.conf.HttpRequestConfigBase;
import com.emc.mongoose.core.impl.load.executor.BasicHttpContainerLoadExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.rmi.RemoteException;
/**
 * Created by gusakk on 21.10.15.
 */
public class BasicHttpContainerLoadBuilder<
	T extends HttpDataItem,
	C extends Container<T>,
	U extends HttpContainerLoadExecutor<T, C>
>
extends ContainerLoadBuilderBase<T, C, U>
implements HttpContainerLoadBuilder<T, C, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicHttpContainerLoadBuilder(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
		setAppConfig(appConfig);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected HttpRequestConfig<T, C> getIoConfig(final AppConfig appConfig) {
		return HttpRequestConfigBase.getInstance(appConfig);
	}
	//
	@Override @SuppressWarnings("CloneDoesntCallSuperClone")
	public final BasicHttpContainerLoadBuilder<T, C, U> clone()
	throws CloneNotSupportedException {
		final BasicHttpContainerLoadBuilder<T, C, U> lb
			= (BasicHttpContainerLoadBuilder<T, C, U>) super.clone();
		LOG.debug(Markers.MSG, "Cloning request config for {}", ioConfig.toString());
		return lb;
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually()
	throws CloneNotSupportedException {
		if(ioConfig == null) {
			throw new IllegalStateException("Should specify request builder instance");
		}
		final HttpRequestConfig ioConfigCopy = (HttpRequestConfig) ioConfig.clone();
		return (U) new BasicHttpContainerLoadExecutor<>(
			appConfig, ioConfigCopy, storageNodeAddrs, threadCount, selectItemInput(ioConfigCopy),
			countLimit, sizeLimit, rateLimit
		);
	}
}
