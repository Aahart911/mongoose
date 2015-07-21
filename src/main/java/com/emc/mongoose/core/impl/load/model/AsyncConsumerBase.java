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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
	private final long maxCount;
	protected final int submTimeOutMilliSec, maxQueueSize, batchSize;
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
		final long maxCount, final int maxQueueSize, final int submTimeOutMilliSec,
		final int batchSize
	) {
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		this.maxQueueSize = (int) Math.min(this.maxCount, maxQueueSize);
		this.submTimeOutMilliSec = submTimeOutMilliSec;
		this.batchSize = batchSize;
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
	public void submit(final T item)
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
	//
	private final Lock bulkInsertLock = new ReentrantLock();
	/**
	 May block the executing thread until the queue becomes able to ingest all the items
	 @param items shouldn't be null!
	 @throws RemoteException
	 @throws InterruptedException if shutdown already
	 @throws RejectedExecutionException if failed to insert at least one item from the specified list
	 */
	@Override
	public void submit(final List<T> items)
	throws RemoteException, InterruptedException, RejectedExecutionException {
		if(isStarted.get()) {
			if(items == null || counterPreSubm.get() >= maxCount) {
				shutdown();
			}
			if(isShutdown.get()) {
				throw new InterruptedException("Shut down already");
			}
			if(bulkInsertLock.tryLock(submTimeOutMilliSec, TimeUnit.MILLISECONDS)) {
				try {
					final long remaining = maxCount - counterPreSubm.get();
					final List<T> items2insert;
					if(remaining < items.size()) {
						items2insert = items.subList(0, (int) remaining); // cast should be safe here
					} else {
						items2insert = items;
					}
					while(items2insert.size() < queue.remainingCapacity()) {
						TimeUnit.MILLISECONDS.sleep(submTimeOutMilliSec);
					}
					if(!queue.addAll(items2insert)) {
						throw new RejectedExecutionException("Failed to perform bulk queue insert");
					}
					counterPreSubm.addAndGet(items2insert.size());
				} finally {
					bulkInsertLock.unlock();
				}
			}
		} else {
			throw new RejectedExecutionException("Consuming is not started yet");
		}
	}
	/** Consumes the queue */
	@Override
	public final void run() {
		LOG.debug(
			Markers.MSG, "Determined submit queue capacity of {} for \"{}\"",
			queue.remainingCapacity(), getName()
		);
		final List<T> itemBuffer = new ArrayList<>(batchSize);
		try {
			// finish if queue is empty and the state is "shutdown"
			while(queue.size() > 0 || !isShutdown.get()) {
				queue.drainTo(itemBuffer);
				if(itemBuffer.size() > 0) {
					submitSync(itemBuffer);
				} else {
					Thread.sleep(submTimeOutMilliSec);
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
	protected abstract void submitSync(final T item)
	throws InterruptedException, RemoteException;
	//
	protected abstract void submitSync(final List<T> item)
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
			LOG.debug(Markers.MSG, "Dropped {} submit tasks", dropCount);
		}
		queue.clear(); // dispose
	}
}
