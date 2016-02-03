package com.emc.mongoose.core.api.load.model;
//
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.core.api.item.base.Item;
import com.emc.mongoose.core.api.load.model.metrics.IOStats;
//
import java.io.Serializable;
/**
 * Created by gusakk on 19.06.15.
 */
public interface LoadState<T extends Item>
extends Serializable {
	//
	int getLoadNumber();
	//
	BasicConfig getAppConfig();
	//
	IOStats.Snapshot getStatsSnapshot();
	//
	T getLastDataItem();
	//
	boolean isLimitReached(final BasicConfig rtConfig);
	//
	interface Builder<T extends Item, U extends LoadState<T>> {
		//
		Builder<T, U> setLoadNumber(final int loadNumber);
		//
		Builder<T, U> setAppConfig(final BasicConfig appConfig);
		//
		Builder<T, U> setStatsSnapshot(final IOStats.Snapshot ioStatsSnapshot);
		//
		Builder<T, U> setLastDataItem(final T dataItem);
		//
		U build();
	}
}
