package com.emc.mongoose.core.impl.load.model;
//
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.item.base.Item;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.api.load.model.LoadState;
import com.emc.mongoose.core.api.load.model.metrics.IOStats;
//
import com.emc.mongoose.core.impl.load.tasks.LoadCloseHook;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
/**
 * Created by gusakk on 19.06.15.
 */
public class BasicLoadState<T extends Item>
implements LoadState<T> {
	//
	private final int loadNumber;
	private final BasicConfig appConfig;
	private final IOStats.Snapshot ioStatsSnapshot;
	private final T lastDataItem;
	//
	@Override
	public int getLoadNumber() {
		return loadNumber;
	}
	//
	public BasicConfig getAppConfig() {
		return appConfig;
	}
	//
	@Override
	public IOStats.Snapshot getStatsSnapshot() {
		return ioStatsSnapshot;
	}
	//
	@Override
	public T getLastDataItem() {
		return lastDataItem;
	}
	//
	@Override
	public boolean isLimitReached(final BasicConfig rtConfig) {
		//  time limitations
		final TimeUnit loadLimitTimeUnit = rtConfig.getLoadLimitTimeUnit();
		final long loadLimitTimeValue = rtConfig.getLoadLimitTimeValue();
		final long loadTimeMicroSec = loadLimitTimeValue > 0 ?
			loadLimitTimeUnit.toMicros(loadLimitTimeValue) : Long.MAX_VALUE;
		final long stateTimeMicroSec = ioStatsSnapshot.getElapsedTime();
		//  count limitations
		final long counterResults = ioStatsSnapshot.getSuccCount() + ioStatsSnapshot.getFailCount();
		final long loadLimitCount = rtConfig.getLoadLimitCount();
		final long maxCount = loadLimitCount > 0 ?
			rtConfig.getLoadLimitCount() : Long.MAX_VALUE;
		return (counterResults >= maxCount) || (stateTimeMicroSec >= loadTimeMicroSec);
	}
	//
	public static class Builder<T extends Item, U extends BasicLoadState<T>>
	implements LoadState.Builder<T, U> {
		//
		private int loadNumber;
		private BasicConfig appConfig;
		private IOStats.Snapshot ioStatsSnapshot;
		private T lastDataItem;
		//
		@Override
		public Builder<T, U> setLoadNumber(final int loadNumber) {
			this.loadNumber = loadNumber;
			return this;
		}
		//
		public Builder<T, U> setAppConfig(final BasicConfig appConfig) {
			this.appConfig = appConfig;
			return this;
		}
		//
		@Override
		public Builder<T, U> setStatsSnapshot(final IOStats.Snapshot ioStatsSnapshot) {
			this.ioStatsSnapshot = ioStatsSnapshot;
			return this;
		}
		//
		@Override
		public Builder<T, U> setLastDataItem(final T lastDataItem) {
			this.lastDataItem = lastDataItem;
			return this;
		}
		//
		@Override
		@SuppressWarnings("unchecked")
		public U build() {
			return (U) new BasicLoadState<>((Builder<T, BasicLoadState<T>>) this);
		}
		//
	}
	//
	private BasicLoadState(final Builder<T, BasicLoadState<T>> builder) {
		this.loadNumber = builder.loadNumber;
		this.appConfig = builder.appConfig;
		this.ioStatsSnapshot = builder.ioStatsSnapshot;
		this.lastDataItem = builder.lastDataItem;
	}
	//
	private static final Logger LOG = LogManager.getLogger();
	//
	public static void restoreScenarioState(final BasicConfig rtConfig) {
		final String fullStateFileName = Paths.get(
			BasicConfig.DIR_ROOT, Constants.DIR_LOG, rtConfig.getRunId()
		).resolve(Constants.STATES_FILE).toString();
		//  if load states list is empty or file w/ load states doesn't exist, then init
		//  map entry value w/ empty list
		LoadExecutor.RESTORED_STATES_MAP.put(
			rtConfig.getRunId(), new ArrayList<LoadState<? extends Item>>()
		);
		if(isSavedStateOfRunExists(rtConfig.getRunId())) {
			final List<LoadState<? extends Item>>
				loadStates = getRunStateFromFile(rtConfig.getRunId(), fullStateFileName);
			if(loadStates != null && !loadStates.isEmpty()) {
				//  check if immutable params were changed for load executors
				for(final LoadState state : loadStates) {
					if(rtConfig.isImmutableParamsChanged(state.getAppConfig())) {
						LOG.warn(
							Markers.MSG,
							"Run \"{}\": configuration immutability violated. Starting new run",
							rtConfig.getRunId()
						);
						return;
					}
				}
				//  override load states list
				LoadExecutor.RESTORED_STATES_MAP.put(rtConfig.getRunId(), loadStates);
				LOG.info(Markers.MSG, "Run \"{}\" was resumed", rtConfig.getRunId());
				//  don't remove state file if load executor has been already finished
				for(final LoadState state : loadStates) {
					if (state.isLimitReached(rtConfig))
						return;
				}
				//  remove state file when scenario's state was restored
				removePrevStateFile(fullStateFileName);
			}
		} else {
			LOG.info(Markers.MSG, "Could not find saved state of run \"{}\". Starting new run",
				rtConfig.getRunId());
		}
	}
	//
	public static boolean isSavedStateOfRunExists(final String runId) {
		final String fullStateFileName = Paths.get(BasicConfig.DIR_ROOT,
			Constants.DIR_LOG, runId).resolve(Constants.STATES_FILE).toString();
		final File stateFile = new File(fullStateFileName);
		return stateFile.exists();
	}
	//
	@SuppressWarnings("unchecked")
	private static List<LoadState<? extends Item>> getRunStateFromFile(
		final String runId, final String fileName
	) {
		try(final FileInputStream fis = new FileInputStream(fileName)) {
			try (final ObjectInputStream ois = new ObjectInputStream(fis)) {
				return (List<LoadState<?>>) ois.readObject();
			}
		} catch (final FileNotFoundException e) {
			LOG.debug(
				Markers.MSG, "Could not find saved state of run \"{}\". Starting new run", runId
			);
		} catch (final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e,
				"Failed to load state of run \"{}\" from \"{}\" file." +
					"Starting new run", runId, fileName);
		} catch (final ClassNotFoundException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to deserialize state of run." +
				"Starting new run");
		}
		return null;
	}
	//
	private static void removePrevStateFile(final String fileName) {
		try {
			Files.delete(Paths.get(fileName));
		} catch (final NoSuchFileException e) {
			LogUtil.exception(LOG, Level.WARN, e,
				"File \"{}\" with state of run wasn't found", fileName);
		} catch (final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e,
				"Failed to remove the file \"{}\"", fileName);
		}
	}
	//
	@SuppressWarnings("unchecked")
	public static <T extends Item> LoadState<T> findStateByLoadNumber(
		final int loadNumber, final BasicConfig rtConfig
	) {
		final List<LoadState<?>>
			loadStates = LoadExecutor.RESTORED_STATES_MAP.get(rtConfig.getRunId());
		for(final LoadState<? extends Item> state : loadStates) {
			if(state.getLoadNumber() == loadNumber) {
				return (LoadState<T>) state;
			}
		}
		return null;
	}
	//
	public static boolean isRunFinished(
		final BasicConfig rtConfig, final List<LoadState> states
	) {
		final TimeUnit loadLimitTimeUnit = rtConfig.getLoadLimitTimeUnit();
		final long loadLimitTimeValue = rtConfig.getLoadLimitTimeValue();
		final long timeLimitMicroSec = loadLimitTimeValue > 0 ?
			loadLimitTimeUnit.toMicros(rtConfig.getLoadLimitTimeValue()) : Long.MAX_VALUE;
		final long loadLimitCount = rtConfig.getLoadLimitCount() > 0 ?
			rtConfig.getLoadLimitCount() : Long.MAX_VALUE;
		//
		for(final LoadState state : states) {
			final IOStats.Snapshot statsSnapshot = state.getStatsSnapshot();
			if (statsSnapshot == null) {
				return true;
			}
			final long elapsedTimeMicroSec = statsSnapshot.getElapsedTime();
			final long stateItemsCount = statsSnapshot.getSuccCount() + statsSnapshot.getFailCount();
			if(elapsedTimeMicroSec >= timeLimitMicroSec) {
				LOG.debug(
					Markers.MSG, "Elapsed time {} is not less than the limit {}",
					elapsedTimeMicroSec, timeLimitMicroSec
				);
				return true;
			}
			if(stateItemsCount >= loadLimitCount) {
				LOG.debug(
					Markers.MSG, "Processed items count {} is not less than the limit {}",
					elapsedTimeMicroSec, timeLimitMicroSec
				);

				return true;
			}
		}
		return false;
	}
	//
	public static void saveRunState(final String runId, final List<LoadState> loadStates) {
		final String fullStateFileName = Paths
			.get(BasicConfig.DIR_ROOT, Constants.DIR_LOG, runId)
			.resolve(Constants.STATES_FILE)
			.toString();
		try(final FileOutputStream fos = new FileOutputStream(fullStateFileName, false)) {
			try(final ObjectOutputStream oos = new ObjectOutputStream(fos)) {
				synchronized(LoadCloseHook.LOAD_STATES_MAP) {
					oos.writeObject(loadStates);
				}
			}
			LOG.info(Markers.MSG, "Successfully saved state of run \"{}\"", runId);
		} catch (final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to save state of run \"{}\"", runId);
		}
	}
}
