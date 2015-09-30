package com.emc.mongoose.core.impl.data.model;
//
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.FileDataItemSrc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
/**
 An item input implementation deserializing the data items from the specified file.
 */
public class BinFileItemSrc<T extends DataItem>
extends BinItemSrc<T>
implements FileDataItemSrc<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final Path itemsSrcPath;
	/**
	 @param itemsSrcPath the path to the file which should be used to restore the serialized items
	 @throws IOException if unable to open the file for reading
	 */
	public BinFileItemSrc(final Path itemsSrcPath)
	throws IOException {
		super(
			buildObjectInputStream(itemsSrcPath)
		);
		this.itemsSrcPath = itemsSrcPath;
	}
	//
	protected static ObjectInputStream buildObjectInputStream(final Path itemsSrcPath)
	throws IOException {
		return new ObjectInputStream(
			new BufferedInputStream(
				Files.newInputStream(itemsSrcPath, StandardOpenOption.READ)
			)
		);
	}
	//
	@Override
	public String toString() {
		return "binFileItemInput<" + itemsSrcPath.getFileName() + ">";
	}
	//
	@Override
	public Path getFilePath() {
		return itemsSrcPath;
	}
	//
	@Override
	public long getApproxDataItemsSize(final int maxCount) {
		long sumSize = 0;
		int actualCount = 0;
		try(final FileDataItemSrc<T> nestedItemSrc = new BinFileItemSrc<>(itemsSrcPath)) {
			final List<T> firstItemsBatch = new ArrayList<>(maxCount);
			actualCount = nestedItemSrc.get(firstItemsBatch, maxCount);
			for(final T nextItem : firstItemsBatch) {
				sumSize += nextItem.getSize();
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to get approx data items size");
		}
		return actualCount > 0 ? sumSize / actualCount : 0;
	}
	//
	@Override
	public void reset()
	throws IOException {
		if (itemsSrc != null) {
			itemsSrc.close();
		}
		setItemsSrc(buildObjectInputStream(itemsSrcPath));
	}
}