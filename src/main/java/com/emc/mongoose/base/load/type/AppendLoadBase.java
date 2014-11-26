package com.emc.mongoose.base.load.type;
//
import com.emc.mongoose.base.api.RequestConfig;
import com.emc.mongoose.base.data.AppendableDataItem;
import com.emc.mongoose.base.load.impl.LoadExecutorBase;
import com.emc.mongoose.util.conf.RunTimeConfig;
import com.emc.mongoose.util.logging.Markers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.rmi.RemoteException;
import java.util.concurrent.ThreadLocalRandom;
/**
 Created by kurila on 20.10.14.
 */
public abstract class AppendLoadBase<T extends AppendableDataItem>
extends LoadExecutorBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final long minAppendSize, maxAppendSize;
	//
	protected AppendLoadBase(
		final RunTimeConfig runTimeConfig,
		final String[] addrs, final RequestConfig<T> sharedReqConf, final long maxCount,
		final int threadsPerNode, final String listFile,
		final long minAppendSize, final long maxAppendSize
	) {
		super(runTimeConfig, addrs, sharedReqConf, maxCount, threadsPerNode, listFile);
		this.minAppendSize = minAppendSize;
		this.maxAppendSize = maxAppendSize;
	}
	//
	@Override
	public void submit(final T dataItem)
	throws RemoteException, InterruptedException {
		if(dataItem!=null) {
			final long appendSize = ThreadLocalRandom
				.current()
				.nextLong(minAppendSize, maxAppendSize + 1);
			dataItem.append(appendSize);
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "Append the object \"{}\": +{}",
					dataItem, RunTimeConfig.formatSize(appendSize)
				);
			}
		}
		super.submit(dataItem);
	}
	//
}
