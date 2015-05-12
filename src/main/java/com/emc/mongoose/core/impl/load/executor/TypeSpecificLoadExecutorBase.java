package com.emc.mongoose.core.impl.load.executor;
//
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.req.conf.RequestConfig;
import com.emc.mongoose.core.api.data.AppendableDataItem;
import com.emc.mongoose.core.api.data.UpdatableDataItem;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.logging.LogUtil;
//
import com.emc.mongoose.core.impl.load.model.FileProducer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
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
		final RunTimeConfig runTimeConfig, final RequestConfig<T> reqConfig, final String[] addrs,
		final int connCountPerNode, final String listFile, final long maxCount,
		final long sizeMin, final long sizeMax, final float sizeBias,
		final float rateLimit, final int countUpdPerReq
	) throws ClassCastException {
		super(
			runTimeConfig, reqConfig, addrs, connCountPerNode, listFile, maxCount,
			sizeMin, sizeMax, sizeBias, rateLimit
		);
		//
		this.loadType = reqConfig.getLoadType();
		//
		int buffSize;
		if(producer != null && FileProducer.class.isInstance(producer)) {
			final long approxDataItemSize = ((FileProducer) producer).getApproxDataItemsSize();
			if(approxDataItemSize < BUFF_SIZE_LO) {
				buffSize = BUFF_SIZE_LO;
			} else if(approxDataItemSize > BUFF_SIZE_HI) {
				buffSize = BUFF_SIZE_HI;
			} else {
				buffSize = (int) approxDataItemSize;
			}
		} else {
			if(sizeMin == sizeMax) {
				LOG.debug(LogUtil.MSG, "Fixed data item size: {}", SizeUtil.formatSize(sizeMin));
				buffSize = sizeMin < BUFF_SIZE_HI ? (int) sizeMin : BUFF_SIZE_HI;
			} else {
				final long t = (sizeMin + sizeMax) / 2;
				buffSize = t < BUFF_SIZE_HI ? (int) t : BUFF_SIZE_HI;
				LOG.debug(
					LogUtil.MSG, "Average data item size: {}",
					SizeUtil.formatSize(buffSize)
				);
			}
			if(buffSize < BUFF_SIZE_LO) {
				LOG.debug(
					LogUtil.MSG, "Buffer size {} is less than lower bound {}",
					SizeUtil.formatSize(buffSize), SizeUtil.formatSize(BUFF_SIZE_LO)
				);
				buffSize = BUFF_SIZE_LO;
			}
		}
		LOG.debug(
			LogUtil.MSG, "Determined buffer size of {} for \"{}\"",
			SizeUtil.formatSize(buffSize), getName()
		);
		this.reqConfigCopy.setBuffSize(buffSize);
		//
		switch(loadType) {
			case APPEND:
			case CREATE:
				if(sizeMin < 0) {
					throw new IllegalArgumentException(
						String.format(
							"Min data item size (%s) is less than zero",
							SizeUtil.formatSize(sizeMin)
						)
					);
				}
				if(sizeMin > sizeMax) {
					throw new IllegalArgumentException(
						String.format(
							"Min object size (%s) shouldn't be more than max (%s)",
							SizeUtil.formatSize(sizeMin), SizeUtil.formatSize(sizeMax)
						)
					);
				}
				if(sizeBias < 0) {
					throw new IllegalArgumentException(
						String.format("Object size bias (%f) should not be less than 0", sizeBias)
					);
				}
				break;
			case UPDATE:
				if(countUpdPerReq < 0) {
					throw new IllegalArgumentException(
						String.format("Invalid updates per request count: %d", countUpdPerReq)
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
				if(LOG.isTraceEnabled(LogUtil.MSG)) {
					LOG.trace(
						LogUtil.MSG, "Append the object \"{}\": +{}",
						dataItem, SizeUtil.formatSize(nextSize)
					);
				}
				break;
			case UPDATE:
				if(dataItem.getSize() > 0) {
					dataItem.updateRandomRanges(countUpdPerReq);
					if(LOG.isTraceEnabled(LogUtil.MSG)) {
						LOG.trace(
							LogUtil.MSG, "Modified {} ranges for object \"{}\"",
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