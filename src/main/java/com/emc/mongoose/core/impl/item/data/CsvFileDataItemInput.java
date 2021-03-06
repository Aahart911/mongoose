package com.emc.mongoose.core.impl.item.data;
//
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.core.api.item.data.DataItem;
import com.emc.mongoose.core.api.item.data.ContentSource;
import com.emc.mongoose.core.api.item.data.FileDataItemInput;
//
import com.emc.mongoose.core.impl.item.base.CsvFileItemInput;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
/**
 Created by kurila on 20.10.15.
 */
public class CsvFileDataItemInput<T extends DataItem>
extends CsvFileItemInput<T>
implements FileDataItemInput<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	/**
	 @param itemsFilePath the input stream to get the data item records from
	 @param itemCls the particular data item implementation class used to parse the records
	 @param contentSrc
	 @throws IOException
	 @throws NoSuchMethodException */
	public CsvFileDataItemInput(
		final Path itemsFilePath, final Class<? extends T> itemCls, final ContentSource contentSrc
	) throws IOException, NoSuchMethodException {
		super(itemsFilePath, itemCls, contentSrc);
	}
	//
	@Override
	public long getAvgDataSize(final int maxCount) {
		long sumSize = 0;
		int actualCount = 0;
		try(
			final FileDataItemInput<T> nestedItemSrc = new CsvFileDataItemInput<>(
				itemsFilePath, itemConstructor.getDeclaringClass(), contentSrc
			)
		) {
			final List<T> firstItemsBatch = new ArrayList<>(maxCount);
			actualCount = nestedItemSrc.get(firstItemsBatch, maxCount);
			for(final T nextItem : firstItemsBatch) {
				sumSize += nextItem.getSize();
			}
		} catch(final EOFException ignore) {
		} catch(final IOException | NoSuchMethodException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to get approx data items size");
		}
		return actualCount > 0 ? sumSize / actualCount : 0;
	}
	//
	@Override
	public String toString() {
		return "csvFileDataItemInput<" + itemsFilePath.getFileName() + ">";
	}
}
