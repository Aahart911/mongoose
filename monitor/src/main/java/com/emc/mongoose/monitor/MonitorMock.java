package com.emc.mongoose.monitor;

import com.emc.mongoose.common.concurrent.LifeCycleBase;
import com.emc.mongoose.common.io.IoTask;
import com.emc.mongoose.common.item.Item;
import com.emc.mongoose.common.load.Driver;
import com.emc.mongoose.common.load.Generator;
import com.emc.mongoose.common.load.Monitor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 Created by kurila on 12.07.16.
 */
public class MonitorMock<I extends Item, O extends IoTask<I>>
extends LifeCycleBase
implements Monitor<I, O> {

	private final List<Generator<I, O>> generators;
	private final ConcurrentMap<String, Driver<I, O>> drivers = new ConcurrentHashMap<>();

	public MonitorMock(final List<Generator<I, O>> generators) {
		this.generators = generators;
		for(final Generator<I, O> generator : generators) {
			generator.registerMonitor(this);
		}
	}
	
	@Override
	public void ioTaskCompleted(final O ioTask) {
	}

	@Override
	public int ioTaskCompletedBatch(final List<O> ioTasks, final int from, final int to) {
		return 0;
	}

	@Override
	public final void registerDriver(final Driver<I, O> driver)
	throws IllegalStateException {
		if(null != drivers.putIfAbsent(driver.toString(), driver)) {
			throw new IllegalStateException("Driver already registered");
		}
	}

	@Override
	public void driverFinished(final Driver<I, O> driver) {

	}

	@Override
	protected void doStart() {
		for(final Generator<I, O> nextGenerator : generators) {
			nextGenerator.start();
		}
	}

	@Override
	protected void doShutdown() {
		for(final Generator<I, O> nextGenerator : generators) {
			nextGenerator.shutdown();
		}
	}

	@Override
	protected void doInterrupt() {
		for(final Generator<I, O> nextGenerator : generators) {
			nextGenerator.interrupt();
		}
	}

	@Override
	public boolean await()
	throws InterruptedException {
		return false;
	}

	@Override
	public boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptedException {
		return false;
	}

	@Override
	public void close()
	throws IOException {
		if(!isInterrupted()) {
			interrupt();
		}
		generators.clear();
		drivers.clear();
	}
}