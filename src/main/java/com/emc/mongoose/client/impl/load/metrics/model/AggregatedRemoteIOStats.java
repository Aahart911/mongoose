package com.emc.mongoose.client.impl.load.metrics.model;
//
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
//
import com.emc.mongoose.common.concurrent.GroupThreadFactory;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.data.DataItem;
//
import com.emc.mongoose.core.impl.load.model.metrics.BasicIOStats;
import com.emc.mongoose.core.impl.load.model.metrics.IOStatsBase;
//
import com.emc.mongoose.server.api.load.executor.LoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 14.09.15.
 */
public class AggregatedRemoteIOStats<T extends DataItem>
extends IOStatsBase {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final Map<String, LoadSvc<T>> loadSvcMap;
	private final Map<String, Snapshot> loadStatsSnapshotMap;
	private final ExecutorService statsLoader;
	//
	private long
		countSucc = 0,
		countFail = 0,
		countByte = 0,
		sumDurMicroSec = 0,
		durationValues[],
		latencyValues[];
	private double
		succRateMean = 0,
		succRateLast = 0,
		failRateMean = 0,
		failRateLast = 0,
		byteRateMean = 0,
		byteRateLast = 0;
	//
	public AggregatedRemoteIOStats(
		final String name, final int serveJmxPort, final Map<String, LoadSvc<T>> loadSvcMap
	) {
		super(name, serveJmxPort);
		this.loadSvcMap = loadSvcMap;
		this.loadStatsSnapshotMap = new HashMap<>(loadSvcMap.size());
		statsLoader = Executors.newFixedThreadPool(
			loadSvcMap.size(), new GroupThreadFactory("statsLoader<" + name + ">", true)
		);
		//
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_SUCC, METRIC_NAME_COUNT),
			new Gauge<Long>() {
				@Override
				public final Long getValue() {
					return countSucc;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_SUCC, METRIC_NAME_RATE, METRIC_NAME_MEAN),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return succRateMean;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_SUCC, METRIC_NAME_RATE, METRIC_NAME_LAST),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return succRateLast;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_FAIL, METRIC_NAME_COUNT),
			new Gauge<Long>() {
				@Override
				public final Long getValue() {
					return countFail;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_FAIL, METRIC_NAME_RATE, METRIC_NAME_MEAN),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return failRateMean;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_FAIL, METRIC_NAME_RATE, METRIC_NAME_LAST),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return failRateLast;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_BYTE, METRIC_NAME_COUNT),
			new Gauge<Long>() {
				@Override
				public final Long getValue() {
					return countByte;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_BYTE, METRIC_NAME_RATE, METRIC_NAME_MEAN),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return byteRateMean;
				}
			}
		);
		metrics.register(
			MetricRegistry.name(name, METRIC_NAME_BYTE, METRIC_NAME_RATE, METRIC_NAME_LAST),
			new Gauge<Double>() {
				@Override
				public final Double getValue() {
					return byteRateLast;
				}
			}
		);
	}
	//
	private final static int COUNT_LIMIT_RETRIES = 100;
	//
	private final class LoadIOStatsSnapshotTask
	implements Runnable {
		//
		private final String loadSvcAddr;
		//
		public LoadIOStatsSnapshotTask(final String loadSvcAddr) {
			this.loadSvcAddr = loadSvcAddr;
		}
		//
		@Override
		public void run() {
			Snapshot loadSvcStatsSnapshot;
			final LoadSvc loadSvc = loadSvcMap.get(loadSvcAddr);
			final Thread currThread = Thread.currentThread();
			currThread.setName(currThread.getName() + "@" + loadSvcAddr);
			int countFailed = 0;
			try {
				while(!currThread.isInterrupted()) {
					try {
						loadSvcStatsSnapshot = loadSvc.getStatsSnapshot();
						if(loadSvcStatsSnapshot != null) {
							if(LOG.isTraceEnabled(Markers.MSG)) {
								LOG.trace(
									Markers.MSG, "Got stats snapshot from {}: {}",
									loadSvcAddr, loadSvcStatsSnapshot
								);
							}
							loadStatsSnapshotMap.put(loadSvcAddr, loadSvcStatsSnapshot);
							countFailed = 0; // reset
						} else {
							LOG.warn(
								Markers.ERR,
								"Failed to load the stats snapshot from the load server @ {}",
								loadSvcAddr
							);
						}
						Thread.sleep(1);
					} catch(final RemoteException e) {
						if(countFailed < COUNT_LIMIT_RETRIES) {
							countFailed++;
							TimeUnit.MILLISECONDS.sleep(countFailed);
						} else {
							LogUtil.exception(
								LOG, Level.WARN, e,
								"Failed to fetch the metrics snapshot from {}", loadSvcAddr
							);
							break;
						}
					}
				}
			} catch(final InterruptedException ignored) {
			}
		}
	}
	//
	@Override
	public final void start() {
		for(final String addr : loadSvcMap.keySet()) {
			statsLoader.submit(new LoadIOStatsSnapshotTask(addr));
		}
		statsLoader.shutdown();
		super.start();
	}
	//
	@Override
	public final void markSucc(
		final long size, final int duration, final int latency
	) {
	}
	//
	@Override
	public final void markSucc(
		final long count, final long bytes, final long durationValues[], final long latencyValues[]
	) {
	}
	//
	@Override
	public final void markFail() {
	}
	//
	@Override
	public final void markFail(final long count) {
	}
	//
	@Override
	public final Snapshot getSnapshot() {
		//
		countSucc = 0;
		countFail = 0;
		countByte = 0;
		sumDurMicroSec = 0;
		succRateMean = 0;
		succRateLast = 0;
		failRateMean = 0;
		failRateLast = 0;
		byteRateMean = 0;
		byteRateLast = 0;
		//
		Snapshot loadStatsSnapshot;
		for(final String addr : loadStatsSnapshotMap.keySet()) {
			loadStatsSnapshot = loadStatsSnapshotMap.get(addr);
			if(loadStatsSnapshot != null) {
				countSucc += loadStatsSnapshot.getSuccCount();
				countFail += loadStatsSnapshot.getFailCount();
				countByte += loadStatsSnapshot.getByteCount();
				sumDurMicroSec += loadStatsSnapshot.getDurationSum();
				succRateMean += loadStatsSnapshot.getSuccRateMean();
				succRateLast += loadStatsSnapshot.getSuccRateLast();
				failRateMean += loadStatsSnapshot.getFailRateMean();
				failRateLast += loadStatsSnapshot.getFailRateLast();
				byteRateMean += loadStatsSnapshot.getByteRateMean();
				byteRateLast += loadStatsSnapshot.getByteRateLast();
				durationValues = loadStatsSnapshot.getDurationValues();
				if(durationValues != null) {
					for(final long duration : durationValues) {
						reqDuration.update(duration);
					}
				} else {
					LOG.warn(
						Markers.ERR, "No duration values snapshot is available for {}", addr
					);
				}
				latencyValues = loadStatsSnapshot.getLatencyValues();
				if(latencyValues != null) {
					for(final long latency : latencyValues) {
						respLatency.update(latency);
					}
				} else {
					LOG.warn(
						Markers.ERR, "No latency values snapshot is available for {}", addr
					);
				}
			} else {
				LOG.warn(Markers.ERR, "No load stats snapshot is available for {}", addr);
			}
		}
		//
		final long currElapsedTime = tsStartMicroSec > 0 ?
			TimeUnit.NANOSECONDS.toMicros(System.nanoTime()) - tsStartMicroSec : 0;
		return new BasicIOStats.BasicSnapshot(
			countSucc, succRateLast, countFail, failRateLast, countByte, byteRateLast,
			prevElapsedTimeMicroSec + currElapsedTime, sumDurMicroSec,
			reqDuration.getSnapshot(), respLatency.getSnapshot()
		);
	}
	//
	@Override
	public final void close()
	throws IOException {
		super.close();
		statsLoader.shutdownNow();
	}
}
