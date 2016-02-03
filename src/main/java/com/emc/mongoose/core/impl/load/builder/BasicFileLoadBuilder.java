package com.emc.mongoose.core.impl.load.builder;
//
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.conf.SizeUtil;
//
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.io.conf.FileIOConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.load.executor.FileLoadExecutor;
//
import com.emc.mongoose.core.impl.io.conf.BasicFileIOConfig;
import com.emc.mongoose.core.impl.load.executor.BasicFileLoadExecutor;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
/**
 Created by kurila on 26.11.15.
 */
public class BasicFileLoadBuilder<T extends FileItem, U extends FileLoadExecutor<T>>
extends DataLoadBuilderBase<T, U> {
	//
	public BasicFileLoadBuilder(final BasicConfig rtConfig)
	throws RemoteException {
		super(rtConfig);
	}
	//
	@Override
	protected FileIOConfig<T, ? extends Directory<T>> getDefaultIOConfig() {
		return new BasicFileIOConfig<>();
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		// create parent directories
		final String parentDirectories = ioConfig.getNamePrefix();
		if(parentDirectories != null && !parentDirectories.isEmpty()) {
			try {
				Files.createDirectories(Paths.get(parentDirectories));
			} catch(final IOException e) {
				throw new IllegalStateException(
					"Failed to create target directories @ \"" + parentDirectories + "\""
				);
			}
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually() {
		if(minObjSize > maxObjSize) {
			throw new IllegalStateException(
				String.format(
					"Min object size (%s) shouldn't be more than max (%s)",
					SizeUtil.formatSize(minObjSize), SizeUtil.formatSize(maxObjSize)
				)
			);
		}
		return (U) new BasicFileLoadExecutor<>(
			BasicConfig.getContext(), (FileIOConfig<T, ? extends Directory<T>>) ioConfig,
			null, 0, threadCount, itemSrc == null ? getDefaultItemSource() : itemSrc,
			limitCount, minObjSize, maxObjSize, objSizeBias, limitRate, updatesPerItem
		);
	}
}
