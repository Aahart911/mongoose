package com.emc.mongoose.run.webserver;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.LogUtil;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
//
import java.nio.file.Paths;

import static com.emc.mongoose.common.conf.Constants.DIR_WEBAPP;
import static com.emc.mongoose.common.conf.Constants.DIR_WEBINF;
//
/**
 * Created by gusakk on 02/10/14.
 */
public class WebUiRunner
implements Runnable {
	//
	private static final Logger LOG = LogManager.getLogger();
	//
	@Override
	public void run() {
		final Server server = new Server(8080);
		//
		final WebAppContext webAppContext = new WebAppContext();
		webAppContext.setContextPath("/");
		final String rootDir = BasicConfig.getRootDir();
		webAppContext.setResourceBase(Paths.get(rootDir, DIR_WEBAPP).toString());
		webAppContext.setDescriptor(Paths.get(rootDir, DIR_WEBAPP, DIR_WEBINF).toString());
		webAppContext.setParentLoaderPriority(true);
		server.setHandler(webAppContext);
		//
		final AppConfig appConfig = BasicConfig.THREAD_CONTEXT.get();
		final TestIndexer indexerThread = TestIndexer.getInstance(appConfig);
		try {
			indexerThread.start();
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to start the tests indexer");
		}
		//
		try {
			server.start();
			server.join();
		} catch (final Exception e) {
			LogUtil.exception(LOG, Level.FATAL, e, "Web UI service failure");
		} finally {
			indexerThread.interrupt();
		}
	}
}
