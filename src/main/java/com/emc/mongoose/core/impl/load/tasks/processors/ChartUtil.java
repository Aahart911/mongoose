package com.emc.mongoose.core.impl.load.tasks.processors;

import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.core.api.load.model.metrics.IoStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.emc.mongoose.core.api.load.model.metrics.IoStats.METRIC_NAME_BW;
import static com.emc.mongoose.core.api.load.model.metrics.IoStats.METRIC_NAME_DUR;
import static com.emc.mongoose.core.api.load.model.metrics.IoStats.METRIC_NAME_LAT;
import static com.emc.mongoose.core.api.load.model.metrics.IoStats.METRIC_NAME_TP;
import static com.emc.mongoose.core.impl.load.tasks.processors.Metric.bandwidthMetrics;
import static com.emc.mongoose.core.impl.load.tasks.processors.Metric.durationMetrics;
import static com.emc.mongoose.core.impl.load.tasks.processors.Metric.latencyMetrics;
import static com.emc.mongoose.core.impl.load.tasks.processors.Metric.throughputMetrics;

public final class ChartUtil {

	private static final Map<String, Map<String, Map<String, List<Metric>>>>
		CHARTS_MAP = new ConcurrentHashMap<>();

	public static void addCharts(
		final String runId, final String loadJobName, final IoStats.Snapshot metricsSnapshot
	) {
		final Map<String, List<Metric>> loadJobCharts = new LinkedHashMap<>();
		final Map<String, MetricPolylineManager> managers = MetricPolylineManager.MANAGERS;
		if(!managers.containsKey(loadJobName)) {
			managers.put(loadJobName, new MetricPolylineManager());
		}
		final MetricPolylineManager manager = managers.get(loadJobName);
		manager.updatePolylines(metricsSnapshot);
		loadJobCharts.put(METRIC_NAME_LAT, latencyMetrics(manager));
		loadJobCharts.put(METRIC_NAME_DUR, durationMetrics(manager));
		loadJobCharts.put(METRIC_NAME_TP, throughputMetrics(manager));
		loadJobCharts.put(METRIC_NAME_BW, bandwidthMetrics(manager));
		putCharts(runId, loadJobName, loadJobCharts);
	}

	public static void addCharts(
		final String runId, final String loadJobName, final IoStats.Snapshot metricsSnapshot,
		final int ordinate
	) {
		final long succCount = metricsSnapshot.getSuccCount();
		if(succCount == 0) {
			return;
		}
		final String itemDataSize = SizeInBytes.formatFixedSize(
			metricsSnapshot.getByteCount() / metricsSnapshot.getSuccCount()
		);
		final Map<String, List<Metric>> loadJobCharts = new LinkedHashMap<>();
		final Map<String, Map<String, BasicPolylineManager>> managers = BasicPolylineManager
			.MANAGERS;
		if(!managers.containsKey(loadJobName)) {
			managers.put(loadJobName, new LinkedHashMap<String, BasicPolylineManager>());
		}
		final Map<String, BasicPolylineManager> managersBySize = managers.get(loadJobName);
		if(!managersBySize.containsKey(itemDataSize)) {
			managersBySize.put(itemDataSize, new BasicPolylineManager());
		}
		final BasicPolylineManager manager = managersBySize.get(itemDataSize);
		manager.updatePolylines(ordinate, metricsSnapshot);
		loadJobCharts.put(METRIC_NAME_LAT, latencyMetrics(managersBySize));
		loadJobCharts.put(METRIC_NAME_DUR, durationMetrics(managersBySize));
		loadJobCharts.put(METRIC_NAME_TP, throughputMetrics(managersBySize));
		loadJobCharts.put(METRIC_NAME_BW, bandwidthMetrics(managersBySize));
		putCharts(runId, loadJobName, loadJobCharts);
	}

	private static void putCharts(final String runId, final String loadJobName,
		final Map<String, List<Metric>> charts) {
		if(!CHARTS_MAP.containsKey(runId)) {
			final Map<String, Map<String, List<Metric>>> runIdCharts = new LinkedHashMap<>();
			runIdCharts.put(loadJobName, charts);
			CHARTS_MAP.put(runId, runIdCharts);
		} else {
			CHARTS_MAP.get(runId).put(loadJobName, charts);
		}
	}

	public static Map<String, Map<String, List<Metric>>> getChart(final String runId) {
		return CHARTS_MAP.get(runId);
	}

}
