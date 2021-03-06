package com.emc.mongoose.core.impl.load.builder;
// mongoose-common.jar
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-impl.jar
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.impl.item.data.CsvFileDataItemInput;
import com.emc.mongoose.core.impl.load.executor.BasicHttpDataLoadExecutor;
import com.emc.mongoose.core.impl.io.conf.HttpRequestConfigBase;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.load.builder.HttpDataLoadBuilder;
import com.emc.mongoose.core.api.load.executor.HttpDataLoadExecutor;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
//
import com.emc.mongoose.core.impl.load.executor.BasicMixedHttpDataLoadExecutor;
import org.apache.http.HttpRequest;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/**
 Created by kurila on 05.05.14.
 */
public class BasicHttpDataLoadBuilder<T extends HttpDataItem, U extends HttpDataLoadExecutor<T>>
extends DataLoadBuilderBase<T, U>
implements HttpDataLoadBuilder<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicHttpDataLoadBuilder(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
		setAppConfig(appConfig);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected HttpRequestConfig<T, ? extends Container<T>> getIoConfig(final AppConfig appConfig) {
		return HttpRequestConfigBase.getInstance(appConfig);
	}
	//
	@Override @SuppressWarnings("CloneDoesntCallSuperClone")
	public final BasicHttpDataLoadBuilder<T, U> clone()
	throws CloneNotSupportedException {
		final BasicHttpDataLoadBuilder<T, U> lb = (BasicHttpDataLoadBuilder<T, U>) super.clone();
		LOG.debug(Markers.MSG, "Cloning request config for {}", ioConfig.toString());
		return lb;
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		((HttpRequestConfig) ioConfig).configureStorage(storageNodeAddrs);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually()
	throws CloneNotSupportedException {
		if(ioConfig == null) {
			throw new IllegalStateException("No I/O configuration instance available");
		}
		final HttpRequestConfig ioConfigCopy = (HttpRequestConfig) ioConfig.clone();
		final LoadType loadType = ioConfigCopy.getLoadType();
		if(LoadType.MIXED.equals(loadType)) {
			final Object inputFilesRaw = appConfig.getProperty(AppConfig.KEY_ITEM_SRC_FILE);
			final List<String> inputFiles;
			if(inputFilesRaw instanceof List) {
				inputFiles = (List<String>) inputFilesRaw;
			} else if(inputFilesRaw instanceof String){
				inputFiles = new ArrayList<>();
				inputFiles.add((String) inputFilesRaw);
			} else {
				throw new IllegalStateException(
					"Invalid configuration parameter type for " + AppConfig.KEY_ITEM_SRC_FILE +
						": \"" + inputFilesRaw + "\""
				);
			}
			final List<String> loadPatterns = (List) appConfig
				.getList(AppConfig.KEY_LOAD_TYPE);
			final Map<LoadType, Input<T>> itemInputMap = new HashMap<>();
			final Map<LoadType, Integer> loadTypeWeightMap = LoadType
				.getMixedLoadWeights(loadPatterns);
			if(inputFiles.size()==1) {
				final Path singleInputPath = Paths.get(inputFiles.get(0));
				for(final LoadType nextLoadType : loadTypeWeightMap.keySet()) {
					try {
						itemInputMap.put(
							nextLoadType,
							LoadType.CREATE.equals(nextLoadType) ?
								getNewItemInput(ioConfigCopy) :
								new CsvFileDataItemInput<>(
									singleInputPath, (Class<T>) ioConfigCopy.getItemClass(),
									ioConfigCopy.getContentSource()
								)
						);
					} catch(final NoSuchMethodException | IOException e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Failed to build new item src");
					}
				}
			} else if(inputFiles.size() == loadPatterns.size()) {
				final Iterator<String> inputFilesIterator = inputFiles.iterator();
				String nextInputFile;
				for(final LoadType nextLoadType : loadTypeWeightMap.keySet()) {
					nextInputFile = inputFilesIterator.next();
					try {
						itemInputMap.put(
							nextLoadType,
							LoadType.CREATE.equals(nextLoadType) && nextInputFile == null ?
								getNewItemInput(ioConfigCopy) :
								new CsvFileDataItemInput<>(
									Paths.get(nextInputFile), (Class<T>) ioConfigCopy.getItemClass(),
									ioConfigCopy.getContentSource()
								)
						);
					} catch(final NoSuchMethodException | IOException e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Failed to build new item src");
					}
				}
			} else {
				throw new IllegalStateException(
					"Unable to map the list of " + inputFiles.size() + " input files to " +
						loadPatterns.size() + " load jobs"
				);
			}
			return (U) new BasicMixedHttpDataLoadExecutor<>(
				appConfig, ioConfigCopy, storageNodeAddrs, threadCount, countLimit, sizeLimit,
				rateLimit, sizeConfig, rangesConfig, loadTypeWeightMap, itemInputMap
			);
		} else {
			return (U) new BasicHttpDataLoadExecutor<>(
				appConfig, ioConfigCopy, storageNodeAddrs, threadCount,
				selectItemInput(ioConfigCopy), countLimit, sizeLimit, rateLimit, sizeConfig,
				rangesConfig
			);
		}
	}
}
