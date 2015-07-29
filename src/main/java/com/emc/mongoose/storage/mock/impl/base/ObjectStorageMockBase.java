package com.emc.mongoose.storage.mock.impl.base;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
//
import com.emc.mongoose.core.api.data.DataObject;
//
import com.emc.mongoose.storage.mock.api.ObjectStorageMock;
//
import org.apache.commons.collections4.map.LRUMap;
//
import java.io.IOException;
import java.util.Map;
/**
 Created by kurila on 03.07.15.
 */
public abstract class ObjectStorageMockBase<T extends DataObject>
extends StorageMockBase<T>
implements ObjectStorageMock<T> {
	//
	protected final Map<String, T> itemIndex;
	protected final int capacity;
	//
	protected ObjectStorageMockBase(final RunTimeConfig rtConfig, final Class<T> itemCls) {
		super(rtConfig, itemCls);
		capacity = rtConfig.getStorageMockCapacity();
		itemIndex = new LRUMap<>(rtConfig.getStorageMockCapacity());
	}
	//
	@Override
	protected void startAsyncConsuming() {
	}
	//
	@Override
	public long getSize() {
		return itemIndex.size();
	}
	//
	@Override
	public long getCapacity() {
		return capacity;
	}
	//
	@Override
	public final void create(final T dataItem) {
		itemIndex.put(dataItem.getId(), dataItem);
	}
	//
	@Override
	public void close()
	throws IOException {
		itemIndex.clear();
		super.close();
	}
}
