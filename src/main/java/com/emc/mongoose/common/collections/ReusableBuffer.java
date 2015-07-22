package com.emc.mongoose.common.collections;
//
import com.emc.mongoose.common.conf.RunTimeConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
/**
 Created by kurila on 22.07.15.
 */
public final class ReusableBuffer<T>
extends ArrayList<T>
implements ReusableList<T> {
	//
	private final static Map<Class<?>, InstancePool<ReusableBuffer>>
		BUFFER_POOL_MAP = new HashMap<>();
	//
	private InstancePool pool = null;
	//
	public ReusableBuffer() {
		super(RunTimeConfig.getContext().getBatchSize());
	}
	//
	@SuppressWarnings("unchecked")
	public static <T> ReusableBuffer<T> getInstance(final Class<T> cls, final int size) {
		InstancePool<ReusableBuffer> pool;
		synchronized(BUFFER_POOL_MAP) {
			pool = BUFFER_POOL_MAP.get(cls);
			if(pool == null) {
				try {
					pool = new InstancePool<>(ReusableBuffer.class.getConstructor());
				} catch(final NoSuchMethodException e) {
					throw new RuntimeException(e);
				}
			}
			BUFFER_POOL_MAP.put(cls, pool);
		}
		return pool.take(pool, size);
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final ReusableBuffer<T> reuse(final Object... args)
	throws IllegalArgumentException, IllegalStateException {
		if(args != null) {
			if(args.length > 1) {
				ensureCapacity(Integer.class.cast(args[1]));
			}
		}
		return this;
	}
	//
	@Override
	public final void release() {
		if(pool == null) {
			throw new IllegalStateException("No pool linked to release self");
		} else {
			pool.release(this);
		}
	}
}
