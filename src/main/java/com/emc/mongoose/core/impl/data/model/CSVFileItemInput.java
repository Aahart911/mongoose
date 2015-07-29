package com.emc.mongoose.core.impl.data.model;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataItemInput;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
/**
 Created by kurila on 30.06.15.
 */
public class CSVFileItemInput<T extends DataItem>
extends CSVItemInput<T> {
	//
	protected final Path itemsFilePath;
	protected final int batchSize;
	//
	protected long avgItemsSize = -1;
	/**
	 @param itemsFilePath the input stream to read the data item records from
	 @param itemCls the particular data item implementation class used to parse the records
	 @throws java.io.IOException
	 @throws NoSuchMethodException */
	public CSVFileItemInput(final Path itemsFilePath, final Class<T> itemCls)
	throws IOException, NoSuchMethodException {
		this(itemsFilePath, itemCls, false, RunTimeConfig.getContext().getBatchSize());
	}
	//
	private CSVFileItemInput(
		final Path itemsFilePath, final Class<T> itemCls, boolean nested, final int batchSize
	) throws IOException, NoSuchMethodException {
		super(Files.newInputStream(itemsFilePath, StandardOpenOption.READ), itemCls);
		this.itemsFilePath = itemsFilePath;
		this.batchSize = batchSize;
		if(!nested) {
			avgItemsSize = estimateAvgItemsSize();
		}
	}
	//
	private long estimateAvgItemsSize()
	throws IOException, NoSuchMethodException {
		try(
			final DataItemInput<T> tmpIn = new CSVFileItemInput<>(
				itemsFilePath, itemConstructor.getDeclaringClass(), true, batchSize
			)
		) {
			final List<T> firstItemsBatch = new ArrayList<>(batchSize);
			final int n = tmpIn.read(firstItemsBatch, batchSize);
			if(n > 0) {
				long sizeSum = 0;
				for(int i = 0; i < n; i++) {
					sizeSum = firstItemsBatch.get(i).getSize();
				}
				return sizeSum / n;
			} else {
				return -1;
			}
		}
	}
	//
	public long getAvgItemsSize() {
		return avgItemsSize;
	}
}
