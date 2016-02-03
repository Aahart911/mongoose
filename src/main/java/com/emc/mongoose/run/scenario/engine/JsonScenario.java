package com.emc.mongoose.run.scenario.engine;
//
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.common.log.Markers;
//
import com.fasterxml.jackson.databind.ObjectMapper;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
/**
 Created by kurila on 02.02.16.
 */
public class JsonScenario
extends SequentialJobContainer {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final static String KEY_NODE_SCENARIO = "scenario";
	private final static String KEY_NODE_SEQUENTIAL = "sequential";
	private final static String KEY_NODE_PARALLEL = "parallel";
	//
	private final static String KEY_NODE_JOB = "job";
	//
	public JsonScenario(final File scenarioSrcFile) {
		final ObjectMapper jsonMapper = new ObjectMapper();
		if(scenarioSrcFile.exists() && scenarioSrcFile.isFile()) {
			try {
				final Map<String, Object> tree = jsonMapper.readValue(scenarioSrcFile, Map.class);
				loadTree(tree, this);
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to read the scenario file @ {}", scenarioSrcFile
				);
			}
		}
	}
	//
	private static void loadTree(final Map<String, Object> node, final JobContainer jobContainer)
	throws IOException {
		for(final String key : node.keySet()) {
			final Object v = node.get(key);
			if(v instanceof Map) {
				final JobContainer subContainer;
				switch(key) {
					case KEY_NODE_SCENARIO:
						subContainer = jobContainer;
						loadTree((Map<String, Object>) v, subContainer);
						break;
					case KEY_NODE_SEQUENTIAL:
						subContainer = new SequentialJobContainer();
						jobContainer.append(subContainer);
						loadTree((Map<String, Object>) v, subContainer);
						break;
					case KEY_NODE_PARALLEL:
						subContainer = new ParallelJobContainer();
						jobContainer.append(subContainer);
						loadTree((Map<String, Object>) v, subContainer);
						break;
					case KEY_NODE_JOB:
						subContainer = new SingleJobContainer((Map<String, Object>) v);
						jobContainer.append(subContainer);
					default:
						LOG.warn(Markers.ERR, "Unexpected node: {}", key);
				}
			} else if(v instanceof List) {
				LOG.warn(Markers.ERR, "{}: list value: {}", key, v);
			} else if(v instanceof Double) {
				LOG.warn(Markers.ERR, "{}: double value: {}", key, v);
			} else if(v instanceof Integer) {
				LOG.warn(Markers.ERR, "{}: integer value: {}", key, v);
			} else if(v instanceof Long) {
				LOG.warn(Markers.ERR, "{}: long value: {}", key, v);
			} else if(v instanceof Boolean) {
				LOG.warn(Markers.ERR, "{}: boolean value: {}", key, v);
			} else if(v instanceof String) {
				LOG.warn(Markers.ERR, "{}: string value: {}", key, v);
			} else if(v == null) {
				LOG.warn(Markers.ERR, "{}: null value: {}", key, v);
			} else {
				LOG.warn(Markers.ERR, "Unexpected node type: {}", v.getClass());
			}
		}
	}
}
