package com.emc.mongoose.core.impl.load.tasks;
//
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.impl.util.RunTimeConfig;
import com.emc.mongoose.core.api.util.log.Markers;
import com.emc.mongoose.core.impl.util.log.TraceLogger;
import com.emc.mongoose.core.impl.util.InstancePool;
import com.emc.mongoose.core.api.util.Reusable;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 Created by kurila on 11.12.14.
 */
public final class RequestResultTask<T extends DataItem>
implements Runnable, Reusable {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static int
		reqTimeOutMilliSec = RunTimeConfig.getContext().getRunReqTimeOutMilliSec();
	//
	private volatile LoadExecutor<T> executor = null;
	private volatile IOTask<T> ioTask = null;
	private volatile Future<IOTask.Status> futureResult = null;
	//
	@Override
	public final void run() {
		IOTask.Status ioTaskStatus = IOTask.Status.FAIL_UNKNOWN;
		if(futureResult != null) {
			try {
				ioTaskStatus = futureResult.get(reqTimeOutMilliSec, TimeUnit.MILLISECONDS);
			} catch(final InterruptedException | CancellationException e) {
				TraceLogger.failure(LOG, Level.TRACE, e, "Request has been cancelled");
			} catch(final ExecutionException e) {
				TraceLogger.failure(
					LOG, Level.DEBUG, e,
					String.format("Task #%d execution failure", ioTask.hashCode())
				);
			} catch(final Exception e) {
				TraceLogger.failure(LOG, Level.WARN, e, "Unexpected failure");
			}
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "Task #{} done w/ result {}",
					ioTask.hashCode(), ioTaskStatus.name()
				);
			}
			if(executor != null) {
				try {
					executor.handleResult(ioTask, ioTaskStatus);
				} catch(final IOException e) {
					TraceLogger.failure(LOG, Level.DEBUG, e, "Request result handling failed");
				}
			}
		} else {
			LOG.warn(Markers.ERR, "Null future result");
		}
		release();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final static InstancePool<RequestResultTask>
		POOL = new InstancePool<>(RequestResultTask.class);
	//
	public static RequestResultTask<? extends DataItem> getInstance(
		final LoadExecutor<? extends DataItem> executor,
		final IOTask<? extends DataItem> ioTask,
		final Future<IOTask.Status> futureResult
	) throws InterruptedException {
		return (RequestResultTask<? extends DataItem>) POOL.take(executor, ioTask, futureResult);
	}
	//
	private final AtomicBoolean isAvailable = new AtomicBoolean(true);
	//
	@Override @SuppressWarnings("unchecked")
	public final RequestResultTask<T> reuse(final Object... args)
	throws IllegalArgumentException, IllegalStateException {
		if(isAvailable.compareAndSet(true, false)) {
			if(args == null) {
				throw new IllegalArgumentException("No arguments for reusing the instance");
			}
			if(args.length > 0) {
				executor = (LoadExecutor<T>) args[0];
			}
			if(args.length > 1) {
				ioTask = (IOTask<T>) args[1];
				if(ioTask == null) {
					throw new IllegalArgumentException("I/O task shouldn't be null");
				}
			}
			if(args.length > 2) {
				futureResult = (Future<IOTask.Status>) args[2];
			}
		} else {
			throw new IllegalStateException("Not yet released instance reuse attempt");
		}
		return this;
	}
	//
	@Override
	public final void release() {
		if(isAvailable.compareAndSet(false, true)) {
			if(ioTask != null) {
				ioTask.release();
			}
			POOL.release(this);
		}
	}
	//
	@Override @SuppressWarnings("NullableProblems")
	public final int compareTo(Reusable another) {
		return another == null ? 1 : hashCode() - another.hashCode();
	}
}
