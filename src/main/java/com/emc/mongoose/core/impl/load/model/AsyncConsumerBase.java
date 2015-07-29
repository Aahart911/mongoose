package com.emc.mongoose.core.impl.load.model;
// mongoose-common.jar
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.load.model.AsyncConsumer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
/**
 Created by kurila on 26.05.15.
 */
public abstract class AsyncConsumerBase<T>
extends Thread
implements AsyncConsumer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	// configuration params
	private final long maxCount, submTimeOutMilliSec;
	protected final int maxQueueSize, batchSize;
	// states
	private final AtomicLong counterPreSubm = new AtomicLong(0);
	protected final AtomicBoolean
		isStarted = new AtomicBoolean(false),
		isShutdown = new AtomicBoolean(false),
		isAllSubm = new AtomicBoolean(false);
	// volatile
	private final BlockingQueue<T> queue;
	//
	public AsyncConsumerBase(
		final long maxCount, final long submTimeOutMilliSec,
		final int maxQueueSize, final int batchSize
	) {
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		this.batchSize = batchSize;
		this.submTimeOutMilliSec = submTimeOutMilliSec > 0 ? submTimeOutMilliSec : Long.MAX_VALUE;
		if(maxQueueSize > 0) {
			this.maxQueueSize = (int) Math.min(this.maxCount, maxQueueSize);
		} else {
			throw new IllegalArgumentException("Invalid max queue size: " + maxQueueSize);
		}
		queue = new ArrayBlockingQueue<>(maxQueueSize);
	}
	//
	@Override
	public void start() {
		if(isStarted.compareAndSet(false, true)) {
			LOG.debug(
				Markers.MSG,
				"{}: started, the further consuming will go through the volatile queue",
				getName()
			);
			super.start();
		}
	}
	/**
	 May block the executing thread until the queue becomes able to ingest more
	 @param item
	 @throws RemoteException
	 @throws InterruptedException
	 @throws RejectedExecutionException
	 */
	@Override
	public void feed(final T item)
	throws RemoteException, InterruptedException, RejectedExecutionException {
		if(isStarted.get()) {
			if(item == null || counterPreSubm.get() >= maxCount) {
				shutdown();
			}
			if(isShutdown.get()) {
				throw new InterruptedException("Shut down already");
			}
			if(queue.offer(item, submTimeOutMilliSec, TimeUnit.MILLISECONDS)) {
				counterPreSubm.incrementAndGet();
			} else {
				throw new RejectedExecutionException("Submit queue timeout");
			}
		} else {
			throw new RejectedExecutionException("Consuming is not started yet");
		}
	}
	/**
	 May block the executing thread until the queue becomes able to ingest all the items
	 @param items shouldn't be null!
	 @throws RemoteException
	 @throws InterruptedException if shutdown already
	 @throws RejectedExecutionException if failed to insert at least one item from the specified list
	 */
	@Override
	public void feedAll(final List<T> items)
	throws RemoteException, InterruptedException, RejectedExecutionException {
		for(final T item : items) {
			feed(item);
		}
	}
	/** Consumes the queue */
	@Override
	public final void run() {
		LOG.debug(
			Markers.MSG, "Determined feeding queue capacity of {} for \"{}\"",
			queue.remainingCapacity(), getName()
		);
		final List<T> itemBuffer = new ArrayList<>(batchSize);
		try {
			// finish if queue is empty and the state is "shutdown"
			while(queue.size() > 0 || !isShutdown.get()) {
				queue.drainTo(itemBuffer, batchSize);
				if(LOG.isTraceEnabled(Markers.MSG)) {
					LOG.trace(
						Markers.MSG, "Got next {} data items to feed them sequentially",
						itemBuffer.size()
					);
				}
				if(itemBuffer.size() > 0) {
					feedSequentiallyAll(itemBuffer);
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(Markers.MSG, "Fed {} data items successfully", itemBuffer.size());
					}
					itemBuffer.clear();
				} else {
					Thread.sleep(10);
				}
			}
			LOG.debug(Markers.MSG, "{}: consuming finished", getName());
		} catch(final InterruptedException e) {
			LOG.debug(Markers.MSG, "{}: consuming interrupted", getName());
		} catch(final RejectedExecutionException e) {
			LOG.debug(Markers.MSG, "{}: consuming rejected", getName());
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.WARN, e, "Submit item failure");
		} finally {
			isAllSubm.set(true);
			shutdown();
		}
	}
	//
	protected abstract void feedSequentially(final T item)
	throws InterruptedException, RemoteException;
	//
	protected abstract void feedSequentiallyAll(final List<T> item)
	throws InterruptedException, RemoteException;
	//
	@Override
	public void shutdown() {
		/*if(!isStarted.get()) {
			throw new IllegalStateException(
				getName() + ": not started yet, but shutdown is invoked"
			);
		} else */if(isShutdown.compareAndSet(false, true)) {
			LOG.debug(Markers.MSG, "{}: consumed {} items", getName(), counterPreSubm.get());
		}
	}
	//
	@Override
	public long getMaxCount() {
		return maxCount;
	}
	//
	@Override
	public synchronized void interrupt() {
		shutdown();
		if(!super.isInterrupted()) {
			super.interrupt();
		}
	}
	//
	@Override
	public void close()
	throws IOException {
		shutdown();
		final int dropCount = queue.size();
		if(dropCount > 0) {
			LOG.debug(Markers.MSG, "Dropped {} data items", dropCount);
		}
		queue.clear(); // dispose
	}
}
