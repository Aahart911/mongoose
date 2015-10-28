package com.emc.mongoose.core.impl.io.task;
//
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.Item;
import com.emc.mongoose.core.api.io.req.RequestConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
//
import com.emc.mongoose.core.api.load.model.metrics.IOStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 Created by kurila on 20.10.15.
 */
public class BasicIOTask<T extends Item>
implements IOTask<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final RequestConfig reqConf;
	protected final IOTask.Type ioType;
	protected final T item;
	protected final String nodeAddr;
	//
	protected volatile IOTask.Status status = IOTask.Status.FAIL_UNKNOWN;
	protected volatile long
		reqTimeStart = 0, reqTimeDone = 0,
		respTimeStart = 0, respDataTimeStart = 0, respTimeDone = 0,
		countBytesDone = 0;
	//
	public BasicIOTask(
		final T item, final String nodeAddr, final RequestConfig reqConf
	) {
		this.reqConf = reqConf;
		this.ioType = reqConf.getLoadType();
		this.item = item;
		this.nodeAddr = nodeAddr;
	}
	//
	@Override
	public final String getNodeAddr() {
		return nodeAddr;
	}
	//
	@Override
	public final T getItem() {
		return item;
	}
	//
	@Override
	public final Status getStatus() {
		return status;
	}
	//
	protected final static ThreadLocal<StringBuilder>
		PERF_TRACE_MSG_BUILDER = new ThreadLocal<StringBuilder>() {
		@Override
		protected final StringBuilder initialValue() {
			return new StringBuilder();
		}
	};
	//
	@Override
	public final void mark(final IOStats ioStats, final boolean logDataReadLatencyAlso) {
		// perf traces logging
		final int
			reqDuration = (int) (respTimeDone - reqTimeStart),
			respLatency = (int) (respTimeStart - reqTimeDone);
		if(respLatency > 0 && reqDuration > respLatency) {
			if(LOG.isInfoEnabled(Markers.PERF_TRACE)) {
				StringBuilder strBuilder = PERF_TRACE_MSG_BUILDER.get();
				if(strBuilder == null) {
					strBuilder = new StringBuilder();
					PERF_TRACE_MSG_BUILDER.set(strBuilder);
				} else {
					strBuilder.setLength(0); // clear/reset
				}
				if(logDataReadLatencyAlso) {
					LOG.info(
						Markers.PERF_TRACE,
						strBuilder
							.append(nodeAddr).append(',')
							.append(item.getName()).append(',')
							.append(countBytesDone).append(',')
							.append(status.code).append(',')
							.append(reqTimeStart).append(',')
							.append(respLatency).append(',')
							.append(respDataTimeStart == 0 ? -1 : respDataTimeStart - reqTimeDone).append(',')
							.append(reqDuration)
							.toString()
					);
				} else {
					LOG.info(
						Markers.PERF_TRACE,
						strBuilder
							.append(nodeAddr).append(',')
							.append(item.getName()).append(',')
							.append(countBytesDone).append(',')
							.append(status.code).append(',')
							.append(reqTimeStart).append(',')
							.append(respLatency).append(',')
							.append(reqDuration)
							.toString()
					);
				}
			}
		}
		// stats refreshing
		if(status == IOTask.Status.SUCC) {
			// update the metrics with success
			if(respLatency > 0 && respLatency > reqDuration) {
				LOG.warn(
					Markers.ERR, "{}: latency {} is more than duration: {}", this, respLatency,
					reqDuration
				);
			}
			ioStats.markSucc(countBytesDone, reqDuration, respLatency);
		}
	}
}
