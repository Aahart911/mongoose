package com.emc.mongoose.core.api.load.executor;
//
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.DataItem;
/**
 Created by kurila on 21.10.15.
 */
public interface ContainerLoadExecutor<T extends DataItem, C extends Container<T>>
extends LoadExecutor<C> {
}
