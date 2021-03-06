package com.emc.mongoose.run.scenario.engine;
//
import com.emc.mongoose.common.conf.AppConfig;
//
import com.emc.mongoose.common.log.Markers;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 Created by kurila on 02.02.16.
 */
public class JsonScenario
extends SequentialJob
implements Scenario {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public JsonScenario(final AppConfig config, final File scenarioSrcFile)
	throws IOException, CloneNotSupportedException {
		this(
			config,
			new ObjectMapper()
				.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
				.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true)
				.<Map<String, Object>>readValue(
					scenarioSrcFile, new TypeReference<Map<String, Object>>(){}
				)
		);
	}
	//
	public JsonScenario(final AppConfig config, final InputStream scenarioInputStream)
	throws IOException, CloneNotSupportedException {
		this(
			config,
			new ObjectMapper()
				.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
				.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true)
				.<Map<String, Object>>readValue(
					scenarioInputStream, new TypeReference<Map<String, Object>>(){}
				)
		);
	}
	//
	public JsonScenario(final AppConfig config, final String scenarioString)
	throws IOException, CloneNotSupportedException {
		this(
			config,
			new ObjectMapper()
				.configure(JsonParser.Feature.ALLOW_COMMENTS, true)
				.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true)
				.<Map<String, Object>>readValue(
					scenarioString, new TypeReference<Map<String, Object>>(){}
				)
		);
	}
	//
	public JsonScenario(final AppConfig config, final Map<String, Object> tree)
	throws IOException, CloneNotSupportedException {
		super(config, overrideFromEnv(validateAgainstSchema(tree)));
	}
	//
	private static final Map<String, Object> validateAgainstSchema(final Map<String, Object> tree) {
		/*final Path schemaPath = Paths.get(
			BasicConfig.getWorkingDir(), DIR_SCENARIO, FNAME_SCENARIO_SCHEMA
		);
		try {
			final JsonSchema scenarioSchema = JsonSchemaFactory
				.newBuilder().freeze().getJsonSchema(schemaPath.toUri().toString());
			final JsonNode jacksonTree = new ObjectMapper().valueToTree(tree);
			scenarioSchema.validate(jacksonTree, true);
		} catch(final ProcessingException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to load the scenario schema");
		}*/
		return tree;
	}
	//
	private final static Pattern PATTERN_ENV_VAR = Pattern.compile(
		"\\$\\{([\\w\\-_\\.!@#%\\^&\\*=\\+\\(\\)\\[\\]~:;'\\\\\\|/<>,\\?]+)\\}"
	);
	private static Map<String, Object> overrideFromEnv(final Map<String, Object> tree) {

		Object value;
		String valueStr;
		Matcher m;
		String propertyName;
		String newValue;
		boolean alteredFlag;

		for(final String key : tree.keySet()) {
			value = tree.get(key);
			if(value instanceof Map) {
				overrideFromEnv((Map<String, Object>) value);
			} else if(value instanceof String) {
				valueStr = (String) value;
				m = PATTERN_ENV_VAR.matcher(valueStr);
				alteredFlag = false;
				while(m.find()) {
					propertyName = m.group(1);
					if(propertyName != null && !propertyName.isEmpty()) {
						newValue = System.getenv(propertyName);
						if(newValue != null) {
							valueStr = valueStr.replace("${" + propertyName + "}", newValue);
							alteredFlag = true;
							LOG.info(
								Markers.MSG, "Key \"{}\": replaced \"{}\" with new value \"{}\"",
								key, propertyName, newValue
							);
						}
					}
				}
				if(alteredFlag) {
					tree.put(key, valueStr);
				}
			}
		}

		return tree;
	}
	//
	@Override
	protected final void loadSubTree(final Map<String, Object> subTree) {
		appendNewJob(subTree, localConfig);
	}
	//
	@Override
	protected final synchronized boolean append(final Job job) {
		if(childJobs.size() == 0) {
			return super.append(job);
		} else {
			return false;
		}
	}
	//
	@Override
	public final void run() {
		super.run();
		LOG.info(Markers.MSG, "Scenario end");
	}
	//
	@Override
	public final String toString() {
		return "jsonScenario#" + hashCode();
	}
}
