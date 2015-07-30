package com.emc.mongoose.common.collections;
// mongoose-common.jar
import com.emc.mongoose.common.log.LogUtil;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
/**
 Created by andrey on 09.06.14.
 A pool for any reusable objects(instances).
 Pooled objects should define "release()" method which will invoke the "release" method putting the object(instance) back into a pool.
 Such instances pool may improve the performance in some cases.
 */
public final class InstancePool<T extends Reusable>
extends LinkedList<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final Constructor<T> constructor;
	private final Object sharedArgs[];
	private final AtomicInteger instCount = new AtomicInteger(0);
	private final Lock lock = new ReentrantLock();
	//
	public InstancePool(final Class<T> cls) {
		Constructor<T> constr = null;
		try {
			constr = cls.getConstructor();
		} catch(final NoSuchMethodException e) {
			LogUtil.exception(
				LOG, Level.ERROR, e, "Failed to get the default constructor for class {}", cls
			);
		}
		constructor = constr;
		sharedArgs = null;
	}
	//
	public InstancePool(final Constructor<T> constructor, final Object... sharedArgs) {
		this.constructor = constructor;
		this.sharedArgs = sharedArgs;
	}
	//
	@SuppressWarnings("unchecked")
	public final T take(final Object... args)
	throws IllegalStateException, IllegalArgumentException {
		T instance;
		lock.lock();
		try {
			instance = poll();
		} finally {
			lock.unlock();
		}
		if(instance == null) {
			try {
				if(sharedArgs == null || sharedArgs.length == 0) {
					instance = constructor.newInstance();
					instCount.incrementAndGet();
				} else if(sharedArgs.length == 1) {
					instance = constructor.newInstance(sharedArgs[0]);
					instCount.incrementAndGet();
				} else {
					throw new IllegalArgumentException("Not implemented");
				}
			} catch(
				final InstantiationException | IllegalAccessException | InvocationTargetException e
			) {
				throw new IllegalStateException("Reusable instantiation failure", e);
			}
		}
		//
		return (T) instance.reuse(args);
	}
	//
	public final void release(final T instance) {
		if(instance != null) {
			lock.lock();
			try {
				add(instance);
			} finally {
				lock.unlock();
			}
		}
	}
	//
	@Override
	public final String toString() {
		return "InstancePool<" + constructor.getDeclaringClass().getCanonicalName() + ">: " +
			size() + " instances are in the pool of " + instCount.get() + " total";
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// the things to simplify pool usage ///////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final static Map<Object, InstancePool> POOL_MAP = new HashMap<>();
	//
	public static <T extends Reusable<T>> InstancePool<T> getInstancePool(final Class<T> cls) {
		InstancePool<T> pool;
		synchronized(POOL_MAP) {
			pool = POOL_MAP.get(cls);
			if(pool == null) {
				pool = new InstancePool<>(cls);
				POOL_MAP.put(cls, pool);
			}
		}
		return pool;
	}
	//
	public static <T extends Reusable<T>> InstancePool<T> getInstancePool(
		final Object key, final Constructor<T> constructor, final Object... sharedArgs
	) {
		InstancePool<T> pool;
		synchronized(POOL_MAP) {
			pool = POOL_MAP.<T>get(key);
			if(pool == null) {
				pool = new InstancePool<>(constructor, sharedArgs);
				POOL_MAP.put(key, pool);
			}
		}
		return pool;
	}
}
