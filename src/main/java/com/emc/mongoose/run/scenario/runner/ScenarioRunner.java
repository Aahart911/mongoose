package com.emc.mongoose.run.scenario.runner;
// mongoose-common.jar
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.BasicConfig;
//
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.run.scenario.Chain;
import com.emc.mongoose.run.scenario.Rampup;
import com.emc.mongoose.run.scenario.Single;
import com.emc.mongoose.run.scenario.engine.JsonScenario;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
/**
 Created by kurila on 12.05.14.
 A scenario runner utility class.
 */
public final class ScenarioRunner
implements Runnable {
	//
	private static final Logger LOG = LogManager.getLogger();
	//
	public void run() {
		final BasicConfig ctxConfig = BasicConfig.getContext();
		if(ctxConfig != null) {
			final String scenarioName = ctxConfig.getScenarioName();
			//
			switch(scenarioName) {
				case Constants.RUN_SCENARIO_SINGLE:
					new Single(ctxConfig).run();
					break;
				case Constants.RUN_SCENARIO_CHAIN:
					new Chain(ctxConfig).run();
					break;
				case Constants.RUN_SCENARIO_RAMPUP:
					new Rampup(ctxConfig).run();
					break;
				default:
					new JsonScenario(new File(scenarioName)).run();
			}
			LOG.info(Markers.MSG, "Scenario end");
		} else {
			throw new NullPointerException("Application config hasn't been initialized");
		}
	}
}
