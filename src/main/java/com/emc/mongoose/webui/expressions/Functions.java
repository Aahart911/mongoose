package com.emc.mongoose.webui.expressions;

import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.TimeUtil;

import java.util.Arrays;
/**
 * Created by gusakk on 10/18/14.
 */
public class Functions {

	public static String getString(final BasicConfig appConfig, final String key) {
		String[] stringArray = appConfig.getStringArray(key);
		if (appConfig.getStringArray(key).length > 1) {
			return convertArrayToString(stringArray);
		}
		if (stringArray.length == 0) {
			return "";
		}
		return stringArray[0];
	}

	public static String getTimeValue(final BasicConfig appConfig, final String key) {
		final String rawValue = appConfig.getString(key);
		return Long.toString(TimeUtil.getTimeValue(rawValue));
	}

	public static String getTimeUnit(final BasicConfig appConfig, final String key) {
		final String rawValue = appConfig.getString(key);
		return TimeUtil.getTimeUnit(rawValue).toString().toLowerCase();
	}

	private static String convertArrayToString(final String[] stringArray) {
		return Arrays.toString(stringArray)
			.replace("[", "")
			.replace("]", "")
			.replace(" ", "")
			.trim();
	}

}
