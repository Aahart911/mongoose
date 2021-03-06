package com.emc.mongoose.core.impl.item.base;
//
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.core.api.item.base.Item;
//
import java.util.List;
/**
 Created by andrey on 28.04.16.
 */
public class SingleItemInput<T extends Item>
implements Input<T> {
	//
	private final T item;
	//
	public SingleItemInput(final T item) {
		this.item = item;
	}
	//
	@Override
	public final T get() {
		return item;
	}
	//
	@Override
	public final int get(final List<T> buffer, final int limit) {
		for(int i = 0; i < limit; i ++) {
			buffer.add(item);
		}
		return limit;
	}
	//
	@Override
	public final void skip(final long count) {
	}
	//
	@Override
	public final void reset() {
	}
	//
	@Override
	public final void close() {
	}
}
