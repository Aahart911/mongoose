package com.emc.mongoose.storage.mock.impl.base;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
//
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.data.DataItem;
//
import com.emc.mongoose.core.impl.data.model.CSVFileItemInput;
import com.emc.mongoose.storage.mock.api.IOStats;
import com.emc.mongoose.storage.mock.api.Storage;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
/**
 Created by kurila on 03.07.15.
 */
public abstract class StorageMockBase<T extends DataItem>
implements Storage<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final RunTimeConfig rtConfig;
	protected final IOStats ioStats;
	protected final int portStart;
	protected final Class<T> itemCls;
	//
	protected StorageMockBase(final RunTimeConfig rtConfig, final Class<T> itemCls) {
		this.rtConfig = rtConfig;
		this.itemCls = itemCls;
		ioStats = new BasicStorageIOStats(rtConfig, this);
		portStart = rtConfig.getApiTypePort(rtConfig.getApiName());
	}
	//
	@Override
	public IOStats getStats() {
		return ioStats;
	}
	//
	@Override
	public void run() {
		try {
			start();
		} finally {
			try {
				await();
			} finally {
				try {
					close();
				} catch(final IOException e) {
					LogUtil.exception(LOG, Level.WARN, e, "Failed to close the storage mock");
				}
			}
		}
	}
	//
	protected void start() {
		startAsyncConsuming();
		loadPersistedDataItems();
		ioStats.start();
		startListening();
	}
	//
	@Override
	public void close()
	throws IOException {
		ioStats.close();
	}
	//
	protected abstract void startAsyncConsuming();
	//
	protected void loadPersistedDataItems() {
		// if there is data src file path
		final Path dataFilePath = Paths.get(rtConfig.getDataSrcFPath());
		final int dataSizeRadix = rtConfig.getDataRadixSize();
		if(null != dataFilePath && !Files.isDirectory(dataFilePath) && Files.exists(dataFilePath)) {
			T nextItem;
			long count = 0;
			try(
				final CSVFileItemInput<T>
					csvFileItemInput = new CSVFileItemInput<>(dataFilePath, itemCls)
			) {
				do {
					nextItem = csvFileItemInput.read();
					// if mongoose is v0.5.0
					if(dataSizeRadix == 0x10) {
						nextItem.setSize(Long.valueOf(String.valueOf(nextItem.getSize()), 0x10));
					}
					create(nextItem);
					count ++;
				} while(null != nextItem);
			} catch(final EOFException e) {
				LOG.debug(Markers.MSG, "Loaded {} data items from file {}", count, dataFilePath);
			} catch(final IOException | NoSuchMethodException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to load the data items from file \"{}\"",
					dataFilePath
				);
			}
		}
	}
	//
	protected abstract void startListening();
	//
	protected abstract void await();
}