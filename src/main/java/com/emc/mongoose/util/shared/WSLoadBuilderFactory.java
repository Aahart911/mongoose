package com.emc.mongoose.util.shared;
//
import com.emc.mongoose.client.api.load.executor.WSDataLoadClient;
//
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.load.builder.WSDataLoadBuilder;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
//
import com.emc.mongoose.core.impl.load.builder.BasicWSLoadBuilder;
//
import com.emc.mongoose.client.impl.load.builder.BasicWSDataLoadBuilderClient;
//
import com.emc.mongoose.server.api.load.executor.WSDataLoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.util.NoSuchElementException;
/**
 Created by kurila on 09.06.15.
 */
public class WSLoadBuilderFactory {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	@SuppressWarnings("unchecked")
	public static <T extends WSObject, U extends LoadExecutor<T>> WSDataLoadBuilder<T, U> getInstance(
		final RunTimeConfig rtConfig
	) {
		final String mode = rtConfig.getRunMode();
		WSDataLoadBuilder<T, U> loadBuilderInstance = null;
		switch(mode) {
			case Constants.RUN_MODE_CLIENT:
			case Constants.RUN_MODE_COMPAT_CLIENT:
				try {
					loadBuilderInstance = (WSDataLoadBuilder) new BasicWSDataLoadBuilderClient<
						T, WSDataLoadSvc<T>, WSDataLoadClient<T, WSDataLoadSvc<T>>
					>(rtConfig);
				} catch(final IOException | NoSuchElementException | ClassCastException e) {
					LogUtil.exception(LOG, Level.FATAL, e, "Failed to create the load builder");
				}
				break;
			default:
				loadBuilderInstance = (WSDataLoadBuilder) new BasicWSLoadBuilder<>(rtConfig);
		}
		return loadBuilderInstance;
	}
}
