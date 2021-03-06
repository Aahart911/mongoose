package com.emc.mongoose.core.impl.load.executor;
//
import com.emc.mongoose.common.concurrent.NamingThreadFactory;
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.DataRangesConfig;
import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.io.conf.FileIoConfig;
import com.emc.mongoose.core.api.io.task.IoTask;
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.load.executor.FileLoadExecutor;
import com.emc.mongoose.core.api.load.executor.MixedLoadExecutor;
import com.emc.mongoose.core.api.load.model.metrics.IoStats;
import com.emc.mongoose.core.impl.load.model.WeightThrottle;
import org.apache.commons.lang.text.StrBuilder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 05.04.16.
 */
public class BasicMixedFileLoadExecutor<F extends FileItem>
extends BasicFileLoadExecutor<F>
implements FileLoadExecutor<F>, MixedLoadExecutor<F> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final WeightThrottle<LoadType> barrier;
	private final Map<LoadType, Integer> loadTypeWeights;
	private final Map<LoadType, FileIoConfig<F, ? extends Directory<F>>>
		reqConfigMap = new HashMap<>();
	protected final Map<LoadType, FileLoadExecutor<F>>
		loadExecutorMap = new HashMap<>();
	//
	public BasicMixedFileLoadExecutor(
		final AppConfig appConfig, final FileIoConfig<F, ? extends Directory<F>> reqConfig,
		final int threadCount, final long countLimit, final long sizeLimit, final float rateLimit,
		final SizeInBytes sizeConfig, final DataRangesConfig rangesConfig,
		final Map<LoadType, Integer> loadTypeWeightMap, final Map<LoadType, Input<F>> itemInputMap
	) {
		super(
			appConfig, reqConfig, threadCount, null, countLimit, sizeLimit, rateLimit,
			sizeConfig, rangesConfig
		);
		//
		this.loadTypeWeights = loadTypeWeightMap;
		this.barrier = new WeightThrottle<>(loadTypeWeights, isInterrupted);
		for(final LoadType nextLoadType : loadTypeWeights.keySet()) {
			final FileIoConfig<F, ? extends Directory<F>> reqConfigCopy;
			try {
				reqConfigCopy = (FileIoConfig<F, ? extends Directory<F>>) reqConfig
					.clone().setLoadType(nextLoadType);
			} catch(final CloneNotSupportedException e) {
				throw new IllegalStateException(e);
			}
			reqConfigMap.put(nextLoadType, reqConfigCopy);
			final BasicFileLoadExecutor<F> nextLoadExecutor = new BasicFileLoadExecutor<F>(
				appConfig, reqConfigCopy, threadCount,
				itemInputMap == null ? null : itemInputMap.get(nextLoadType),
				countLimit, sizeLimit, rateLimit, sizeConfig, rangesConfig
			) {
				@Override
				public final <A extends IoTask<F>> Future submitTask(final A ioTask)
				throws RejectedExecutionException {
					try {
						if(barrier.requestContinueFor(nextLoadType)) {
							return BasicMixedFileLoadExecutor.this.submitTask(ioTask);
						} else {
							throw new RejectedExecutionException(
								"Barrier rejected the item for {} operation" + nextLoadType
							);
						}
					} catch(final InterruptedException e) {
						throw new RejectedExecutionException(e);
					}
				}
				//
				@Override
				public final <A extends IoTask<F>> int submitTasks(
					final List<A> ioTasks, int from, int to
				) throws RejectedExecutionException {
					try {
						if(barrier.requestContinueFor(nextLoadType, to - from)) {
							return BasicMixedFileLoadExecutor.this.submitTasks(
								ioTasks, from, to
							);
						} else {
							throw new RejectedExecutionException(
								"Barrier rejected " + (to - from) + " tasks"
							);
						}
					} catch(final InterruptedException e) {
						throw new RejectedExecutionException(e);
					}
				}
				//
				@Override
				public final void logMetrics(final Marker logMarker) {
				}
			};
			loadExecutorMap.put(nextLoadType, nextLoadExecutor);
		}
	}
	//
	@Override
	public void ioTaskCompleted(final IoTask<F> ioTask)
	throws RemoteException {
		loadExecutorMap
			.get(ioTask.getLoadType())
			.ioTaskCompleted(ioTask);
		super.ioTaskCompleted(ioTask);
	}
	//
	@Override
	public final int ioTaskCompletedBatch(
		final List<? extends IoTask<F>> ioTasks, final int from, final int to
	) throws RemoteException {
		if(ioTasks != null && ioTasks.size() > 0) {
			loadExecutorMap
				.get(ioTasks.get(from).getLoadType())
				.ioTaskCompletedBatch(ioTasks, from, to);
		}
		return super.ioTaskCompletedBatch(ioTasks, from, to);
	}
	//
	@Override
	public void logMetrics(final Marker logMarker) {
		final StrBuilder strb = new StrBuilder(Markers.PERF_SUM.equals(logMarker) ? "Summary:" : "")
			.appendNewLine()
			.appendFixedWidthPadLeft("Weight | ", 9, ' ')
			.appendFixedWidthPadLeft("Load type | ", 12, ' ')
			.appendFixedWidthPadLeft("Done | ", 14, ' ')
			.appendFixedWidthPadLeft("Failed | ", 9, ' ')
			.appendFixedWidthPadLeft("Duration [us] | ", 35, ' ')
			.appendFixedWidthPadLeft("Latency [us] | ", 35, ' ')
			.appendFixedWidthPadLeft("TP [op/s] | ", 25, ' ')
			.appendFixedWidthPadLeft("BW [MB/s]", 22, ' ')
			.appendNewLine()
			.appendPadding(160, '-')
			.appendNewLine();
		FileLoadExecutor nextLoadJob;
		int nextLoadWeight;
		IoStats.Snapshot nextLoadStats;
		for(final LoadType nextLoadType : loadExecutorMap.keySet()) {
			nextLoadWeight = loadTypeWeights.get(nextLoadType);
			nextLoadJob = loadExecutorMap.get(nextLoadType);
			try {
				nextLoadStats = nextLoadJob.getStatsSnapshot();
			} catch(final RemoteException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to get the remote stats snapshot");
				continue;
			}
			strb
				.appendFixedWidthPadLeft(nextLoadWeight + " % | ", 9, ' ')
				.appendFixedWidthPadLeft(nextLoadType.name() + " | ", 12, ' ')
				.appendFixedWidthPadLeft(nextLoadStats.getSuccCount() + " | ", 14, ' ')
				.appendFixedWidthPadLeft(nextLoadStats.getFailCount() + " | ", 9, ' ')
				.appendFixedWidthPadLeft(
					(
						Markers.PERF_SUM.equals(logMarker) ?
							nextLoadStats.toDurSummaryString() : nextLoadStats.toDurString()
					) + " | ", 35, ' '
				)
				.appendFixedWidthPadLeft(
					(
						Markers.PERF_SUM.equals(logMarker) ?
							nextLoadStats.toLatSummaryString() : nextLoadStats.toLatString()
					) + " | ", 35, ' '
				)
				.appendFixedWidthPadLeft(nextLoadStats.toSuccRatesString() + " | ", 25, ' ')
				.appendFixedWidthPadLeft(nextLoadStats.toByteRatesString(), 22, ' ')
				.appendNewLine();
		}
		strb
			.appendPadding(160, '-').appendNewLine()
			.appendFixedWidthPadLeft("100 % | ", 9, ' ')
			.appendFixedWidthPadLeft("TOTAL | ", 12, ' ')
			.appendFixedWidthPadLeft(lastStats.getSuccCount() + " | ", 14, ' ')
			.appendFixedWidthPadLeft(lastStats.getFailCount() + " | ", 9, ' ')
			.appendFixedWidthPadLeft(
				(
					Markers.PERF_SUM.equals(logMarker) ?
						lastStats.toDurSummaryString() : lastStats.toDurString()
				) + " | ", 35, ' '
			)
			.appendFixedWidthPadLeft(
				(
					Markers.PERF_SUM.equals(logMarker) ?
						lastStats.toLatSummaryString() : lastStats.toLatString()
				) + " | ", 35, ' '
			)
			.appendFixedWidthPadLeft(lastStats.toSuccRatesString() + " | ", 25, ' ')
			.appendFixedWidthPadLeft(lastStats.toByteRatesString(), 22, ' ');
		LOG.info(Markers.MSG, strb.toString());
	}
	//
	@Override
	protected void startActually() {
		for(final FileLoadExecutor<F> nextLoadExecutor : loadExecutorMap.values()) {
			try {
				nextLoadExecutor.start();
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to start the load job \"{}\"", nextLoadExecutor
				);
			}
		}
		super.startActually();
	}
	//
	@Override
	protected void interruptActually() {
		for(final FileLoadExecutor<F> nextLoadExecutor : loadExecutorMap.values()) {
			try {
				nextLoadExecutor.interrupt();
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to interrupt the load job \"{}\"", nextLoadExecutor
				);
			}
		}
		super.interruptActually();
	}
	//
	@Override
	protected void shutdownActually() {
		for(final FileLoadExecutor<F> nextLoadExecutor : loadExecutorMap.values()) {
			try {
				nextLoadExecutor.shutdown();
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to shutdown the load job \"{}\"", nextLoadExecutor
				);
			}
		}
		super.shutdownActually();
	}
	//
	@Override
	public boolean await(final long timeOut, final TimeUnit timeUnit)
	throws InterruptedException, RemoteException {
		final ExecutorService awaitExecutor = Executors.newFixedThreadPool(
			loadExecutorMap.size() + 1, new NamingThreadFactory("await<" + getName() + ">", true)
		);
		for(final FileLoadExecutor<F> nextLoadExecutor : loadExecutorMap.values()) {
			awaitExecutor.submit(
				new Runnable() {
					@Override
					public final void run() {
						try {
							nextLoadExecutor.await(timeOut, timeUnit);
						} catch(final RemoteException e) {
							LogUtil.exception(
								LOG, Level.ERROR, e, "Failed to await the load job \"{}\"",
								nextLoadExecutor
							);
						} catch(final InterruptedException e) {
							LOG.debug(Markers.MSG, "{}: await call interrupted", nextLoadExecutor);
						}
					}
				}
			);
		}
		awaitExecutor.submit(
			new Runnable() {
				@Override
				public final void run() {
					try {
						BasicMixedFileLoadExecutor.super.await(timeOut, timeUnit);
					} catch(final InterruptedException e) {
						LOG.debug(Markers.MSG, "{}: await call interrupted", getName());
					} catch(final RemoteException e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Remote await method call failure");
					}
				}
			}
		);
		awaitExecutor.shutdown();
		try {
			return awaitExecutor.awaitTermination(timeOut, timeUnit);
		} finally {
			awaitExecutor.shutdownNow();
		}
	}
	//
	@Override
	protected void closeActually()
	throws IOException {
		for(final FileLoadExecutor<F> nextLoadExecutor : loadExecutorMap.values()) {
			try {
				nextLoadExecutor.close();
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to close the load job \"{}\"", nextLoadExecutor
				);
			}
		}
		super.closeActually();
	}
}
