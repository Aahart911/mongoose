package com.emc.mongoose.core.impl.io.task;
// mongoose-common.jar
import com.emc.mongoose.common.collections.InstancePool;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.io.req.conf.ObjectRequestConfig;
import com.emc.mongoose.core.api.io.task.DataObjectIOTask;
import com.emc.mongoose.core.api.data.DataObject;
import com.emc.mongoose.core.api.io.task.IOTask;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 Created by kurila on 23.12.14.
 */
public class BasicObjectIOTask<T extends DataObject>
extends BasicIOTask<T>
implements DataObjectIOTask<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicObjectIOTask(final ObjectRequestConfig<T> reqConf) {
		super(reqConf);
	}
	//
	public final static Map<ObjectRequestConfig, InstancePool<BasicObjectIOTask>>
		INSTANCE_POOL_MAP = new HashMap<>();
	//
	@SuppressWarnings("unchecked")
	public static <T extends DataObject> IOTask<T> getInstance(
		T dataItem, final ObjectRequestConfig<T> reqConf, final String nodeAddr
	) {
		InstancePool<BasicObjectIOTask> instPool = INSTANCE_POOL_MAP.get(reqConf);
		if(instPool == null) {
			try {
				instPool = new InstancePool<>(
					BasicObjectIOTask.class.getConstructor(ObjectRequestConfig.class), reqConf
				);
				INSTANCE_POOL_MAP.put(reqConf, instPool);
			} catch(final NoSuchMethodException e) {
				throw new IllegalStateException(e);
			}
		}
		//
		return instPool.take(dataItem, nodeAddr);
	}
	//
	@SuppressWarnings("unchecked")
	public static <T extends DataObject> void getInstances(
		List<IOTask<T>> taskBuff, List<T> dataItems, final int maxCount,
		final ObjectRequestConfig<T> reqConf, final String nodeAddr
	) {
		InstancePool<BasicObjectIOTask> instPool = INSTANCE_POOL_MAP.get(reqConf);
		if(instPool == null) {
			try {
				instPool = new InstancePool<>(
					BasicObjectIOTask.class.getConstructor(ObjectRequestConfig.class), reqConf
				);
				INSTANCE_POOL_MAP.put(reqConf, instPool);
			} catch(final NoSuchMethodException e) {
				throw new IllegalStateException(e);
			}
		}
		// TODO work in bulk mode
		for(int i = 0; i < maxCount; i ++) {
			taskBuff.add(instPool.take(dataItems.get(i), nodeAddr));
		}
	}
	//
	@Override
	public void release() {
		final InstancePool<BasicObjectIOTask> instPool = INSTANCE_POOL_MAP.get(reqConf);
		if(instPool == null) {
			throw new IllegalStateException("No pool found to release back");
		} else {
			instPool.release(this);
		}
	}
	//
	@Override
	public final void complete() {
		final String dataItemId = dataItem.getId();
		StringBuilder strBuilder = THRLOC_SB.get();
		if(strBuilder == null) {
			strBuilder = new StringBuilder();
			THRLOC_SB.set(strBuilder);
		} else {
			strBuilder.setLength(0); // clear/reset
		}
		if(
			reqTimeDone >= reqTimeStart &&
			respTimeStart >= reqTimeDone &&
			respTimeDone >= respTimeStart
		) {
			LOG.info(
				Markers.PERF_TRACE,
				strBuilder
					.append(nodeAddr).append(',')
					.append(dataItemId).append(',')
					.append(transferSize).append(',')
					.append(status.code).append(',')
					.append(reqTimeStart).append(',')
					.append(respTimeStart - reqTimeDone).append(',')
					.append(respTimeDone - reqTimeStart)
					.toString()
			);
		} else if(
			status != Status.CANCELLED &&
			status != Status.FAIL_IO &&
			status != Status.FAIL_TIMEOUT &&
			status != Status.FAIL_UNKNOWN
		) {
			LOG.warn(
				Markers.ERR,
				strBuilder
					.append("Invalid trace: ")
					.append(nodeAddr).append(',')
					.append(dataItemId).append(',')
					.append(transferSize).append(',')
					.append(status.code).append(',')
					.append(reqTimeStart).append(',')
					.append(reqTimeDone).append(',')
					.append(respTimeStart).append(',')
					.append(respTimeDone)
					.toString()
			);
		}
		//
		final int reqSleepMilliSec = reqConf.getReqSleepMilliSec();
		if(reqSleepMilliSec > 0) {
			try {
				Thread.sleep(reqSleepMilliSec);
			} catch(final InterruptedException e) {
				LogUtil.exception(LOG, Level.DEBUG, e, "Interrupted request sleep");
			}
		}
	}
}
