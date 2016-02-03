package com.emc.mongoose.webui;
//
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.LogUtil;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
//
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * Created by gusakk on 1/16/15.
 */
public abstract class CommonServlet
extends HttpServlet {
	//
	private final static Logger LOG = LogManager.getLogger();
	private static volatile BasicConfig LAST_RUN_TIME_CONFIG;
	private static final BasicConfig DEFAULT_CFG;
	//
	public static ConcurrentHashMap<String, Thread> THREADS_MAP;
	public static ConcurrentHashMap<String, Boolean> STOPPED_RUN_MODES;
	public static ConcurrentHashMap<String, String> CHARTS_MAP;
	//
	protected BasicConfig appConfig;
	//
	static {
		THREADS_MAP = new ConcurrentHashMap<>();
		STOPPED_RUN_MODES = new ConcurrentHashMap<>();
		CHARTS_MAP = new ConcurrentHashMap<>();
		LAST_RUN_TIME_CONFIG = (BasicConfig) BasicConfig.getContext().clone();
		DEFAULT_CFG = BasicConfig.getContext();
	}
	//
	@Override
	public void init() {
		try {
			super.init();
			appConfig = (BasicConfig) (
				(BasicConfig) getServletContext().getAttribute("rtConfig")
			).clone();
		} catch (final ServletException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Interrupted servlet init method");
		}
	}
	//
	protected void setupRunTimeConfig(final HttpServletRequest request) {
		for (final Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
			if (entry.getValue()[0].trim().isEmpty()) {
				final String[] defaultPropValue = DEFAULT_CFG.getStringArray(entry.getKey());
				if (defaultPropValue.length > 0 && !entry.getKey().equals(BasicConfig.KEY_RUN_ID)) {
					appConfig.set(entry.getKey(), convertArrayToString(defaultPropValue));
				}
				continue;
			}
			if (entry.getValue().length > 1) {
				appConfig.set(entry.getKey(), convertArrayToString(entry.getValue()));
				continue;
			}
			appConfig.set(entry.getKey(), entry.getValue()[0].trim());
		}
	}
	//
	protected String convertArrayToString(final String[] stringArray) {
		return Arrays.toString(stringArray)
				.replace("[", "")
				.replace("]", "")
				.replace(" ", "")
				.trim();
	}
	//
	public static void updateLastRunTimeConfig(final BasicConfig appConfig) {
		LAST_RUN_TIME_CONFIG = (BasicConfig) appConfig.clone();
	}
	//
	public static BasicConfig getLastRunTimeConfig() {
		return LAST_RUN_TIME_CONFIG;
	}
}
