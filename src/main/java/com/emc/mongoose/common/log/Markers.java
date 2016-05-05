package com.emc.mongoose.common.log;
//
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.util.HashMap;
import java.util.Map;

/**
 Created by kurila on 09.06.15.
 */
public interface Markers {
	//
	Marker MSG = MarkerManager.getMarker("msg");
	Marker ERR = MarkerManager.getMarker("err");
	Marker ITEM_LIST = MarkerManager.getMarker("dataList");
	Marker PERF_AVG = MarkerManager.getMarker("perfAvg");
	Marker PERF_SUM = MarkerManager.getMarker("perfSum");
	Marker PERF_TRACE = MarkerManager.getMarker("perfTrace");
	Marker CFG = MarkerManager.getMarker("cfg");
	//
	Map<Marker, String> LOG_FILE_NAMES = new HashMap<Marker, String>() {
		{
			put(MSG, "messages.csv");
			put(ERR, "errors.csv");
			put(ITEM_LIST, "items.csv");
			put(PERF_AVG, "perf.avg.csv");
			put(PERF_SUM, "perf.subm.csv");
			put(PERF_TRACE, "perf.trace.csv");
			put(CFG, "config.log");
		}
	};
}
