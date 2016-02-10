package com.emc.mongoose.core.impl.item.data;
//
import com.emc.mongoose.common.conf.ValueGenerator;
//
import com.emc.mongoose.core.api.item.data.DataItem;
import com.emc.mongoose.core.api.item.data.ContentSource;
import com.emc.mongoose.core.api.item.base.ItemSrc;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
/**
 Created by kurila on 24.07.15.
 */
public final class NewDataItemSrc<T extends DataItem>
implements ItemSrc<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final Constructor<T> itemConstructor;
	private final ValueGenerator<Long> itemIdGenerator;
	private final ContentSource contentSrc;
	private final long minObjSize, maxObjSize, sizeRange;
	private final float objSizeBias;
	private final ThreadLocalRandom thrLocalRnd = ThreadLocalRandom.current();
	private T lastItem = null;
	//
	public NewDataItemSrc(
		final Class<T> dataCls,
		final ValueGenerator<Long> itemIdGenerator, final ContentSource contentSrc,
		final long minObjSize, final long maxObjSize, final float objSizeBias
	) throws NoSuchMethodException, IllegalArgumentException {
		this.itemConstructor = dataCls.getConstructor(Long.class, Long.class, ContentSource.class);
		this.itemIdGenerator = itemIdGenerator;
		this.contentSrc = contentSrc;
		this.minObjSize = minObjSize;
		this.maxObjSize = maxObjSize;
		this.objSizeBias = objSizeBias;
		sizeRange = maxObjSize - minObjSize;
		if(sizeRange < 0) {
			throw new IllegalArgumentException(
				"Min size " + minObjSize + " is greater than max size " + maxObjSize
			);
		}
	}
	//
	public long getApproxDataItemsSize() {
		return minObjSize == maxObjSize ? minObjSize : (minObjSize + maxObjSize / 2);
	}
	//
	private long nextSize() {
		if(minObjSize == maxObjSize) {
			return minObjSize;
		} else {
			if(objSizeBias == 1) {
				return minObjSize + (long) (thrLocalRnd.nextDouble() * sizeRange);
			} else {
				return minObjSize + (long) Math.pow(thrLocalRnd.nextDouble(), objSizeBias) * sizeRange;
			}
		}
	}
	//
	@Override
	public final T get()
	throws IOException {
		try {
			return itemConstructor.newInstance(itemIdGenerator.get(), nextSize(), contentSrc);
		} catch(final InstantiationException|IllegalAccessException|InvocationTargetException e) {
			throw new IOException(e);
		}
	}
	//
	@Override
	public int get(final List<T> buffer, final int maxCount)
	throws IOException {
		try {
			for(int i = 0; i < maxCount; i ++) {
				buffer.add(
					itemConstructor.newInstance(itemIdGenerator.get(), nextSize(), contentSrc)
				);
			}
		} catch(final InstantiationException|IllegalAccessException|InvocationTargetException e) {
			throw new IOException(e);
		}
		return maxCount;
	}
	//
	@Override
	public T getLastItem() {
		return lastItem;
	}
	//
	@Override
	public void setLastItem(final T lastItem) {
		this.lastItem = lastItem;
	}
	/**
	 * Does nothing
	 * @param itemsCount count of items which should be skipped from the beginning
	 * @throws IOException doesn't throw
	 */
	@Override
	public void skip(final long itemsCount)
	throws IOException {
	}
	//
	@Override
	public final void reset() {
	}
	//
	@Override
	public final void close() {
	}
	//
	@Override
	public final String toString() {
		return "newDataItemSrc<" + itemConstructor.getDeclaringClass().getSimpleName() + ">";
	}
}
