package com.emc.mongoose.core.impl.load.executor;
//
import com.emc.mongoose.common.conf.Constants;
// mongoose-common.jar
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.req.conf.RequestConfig;
import com.emc.mongoose.core.api.data.AppendableDataItem;
import com.emc.mongoose.core.api.data.UpdatableDataItem;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
/**
 Created by kurila on 15.12.14.
 */
public abstract class TypeSpecificLoadExecutorBase<T extends AppendableDataItem & UpdatableDataItem>
extends LimitedRateLoadExecutorBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final IOTask.Type loadType;
	private final int countUpdPerReq;
	private final long sizeMin, sizeRange;
	private final float sizeBias;
	//
	protected TypeSpecificLoadExecutorBase(
		final Class<T> dataCls,
		final RunTimeConfig runTimeConfig, final RequestConfig<T> reqConfig, final String[] addrs,
		final int connCountPerNode, final String listFile, final long maxCount,
		final long sizeMin, final long sizeMax, final float sizeBias,
		final float rateLimit, final int countUpdPerReq
	) throws ClassCastException {
		super(
			dataCls,
			runTimeConfig, reqConfig, addrs, connCountPerNode, listFile, maxCount,
			sizeMin, sizeMax, sizeBias, rateLimit
		);
		//
		this.loadType = reqConfig.getLoadType();
		//
		switch(loadType) {
			case APPEND:
			case CREATE:
				if(sizeMin < 0) {
					throw new IllegalArgumentException(
						"Min data item size is less than zero: " + SizeUtil.formatSize(sizeMin)
					);
				}
				if(sizeMin > sizeMax) {
					throw new IllegalArgumentException(
						"Min object size shouldn't be more than max: " +
						SizeUtil.formatSize(sizeMin) + ", " + SizeUtil.formatSize(sizeMax)
					);
				}
				if(sizeBias < 0) {
					throw new IllegalArgumentException(
						"Object size bias should not be less than 0: " + sizeBias
					);
				}
				break;
			case UPDATE:
				if(countUpdPerReq < 0) {
					throw new IllegalArgumentException(
						"Invalid updates per request count: " + countUpdPerReq
					);
				}
				break;
		}
		//
		this.sizeMin = sizeMin;
		sizeRange = sizeMax - sizeMin;
		this.sizeBias = sizeBias;
		this.countUpdPerReq = countUpdPerReq;
	}
	// intercepts the data items which should be scheduled for update or append
	@Override
	public final void submit(final T dataItem)
	throws InterruptedException, RemoteException, RejectedExecutionException {
		switch(loadType) {
			case APPEND:
				final long nextSize = sizeMin +
					(long) (
						Math.pow(ThreadLocalRandom.current().nextDouble(), sizeBias) *
						sizeRange
					);
				dataItem.append(nextSize);
				if(LOG.isTraceEnabled(Markers.MSG)) {
					LOG.trace(
						Markers.MSG, "Append the object \"{}\": +{}",
						dataItem, SizeUtil.formatSize(nextSize)
					);
				}
				break;
			case UPDATE:
				if(dataItem.getSize() > 0) {
					dataItem.updateRandomRanges(countUpdPerReq);
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "Modified {} ranges for object \"{}\"",
							countUpdPerReq, dataItem
						);
					}
				} else {
					throw new RejectedExecutionException(
						"It's impossible to update empty data item"
					);
				}
				break;
		}
		//
		super.submit(dataItem);
	}
}
