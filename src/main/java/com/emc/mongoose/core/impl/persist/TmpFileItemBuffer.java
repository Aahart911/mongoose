package com.emc.mongoose.core.impl.persist;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.concurrent.NamingWorkerFactory;
import com.emc.mongoose.common.logging.LogUtil;
//
import com.emc.mongoose.core.api.load.model.Consumer;
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.persist.DataItemBuffer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
/**
 Created by andrey on 28.09.14.
 */
public class TmpFileItemBuffer<T extends DataItem>
extends ThreadPoolExecutor
implements DataItemBuffer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final File fBuff;
	private final AtomicLong writtenDataItems = new AtomicLong(0);
	private volatile long maxCount;
	private volatile ObjectOutput fBuffOut;
	private volatile int retryCountMax, retryDelayMilliSec;
	//
	public TmpFileItemBuffer(final long maxCount, final int threadCount) {
		super(
			threadCount, threadCount, 0, TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(
				maxCount > 0 ?
					maxCount > Integer.MAX_VALUE ?
						RunTimeConfig.getContext().getRunRequestQueueSize()
						: (int) maxCount
					: RunTimeConfig.getContext().getRunRequestQueueSize()
			)
		);
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		//
		final RunTimeConfig localRunTimeConfig = RunTimeConfig.getContext();
		retryCountMax = localRunTimeConfig.getRunRetryCountMax();
		retryDelayMilliSec = localRunTimeConfig.getRunRetryDelayMilliSec();
		//
		File localFBuff = null;
		try {
			localFBuff = Files.createTempFile(
				String.format(FMT_THREAD_NAME, localRunTimeConfig.getRunName()), null
			).toFile();
		} catch(final IOException e) {
			LogUtil.failure(LOG, Level.ERROR, e, "Failed to create temporary file for output");
		}
		fBuff = localFBuff;
		LOG.debug(LogUtil.MSG, "{}: created temp file", toString());
		//
		if(fBuff != null) {
			setThreadFactory(
				new NamingWorkerFactory(fBuff.getName()) // the name should be URL-safe
			);
		}
		//
		ObjectOutput fBuffOutTmp = null;
		if(fBuff != null) {
			try {
				fBuffOutTmp = new ObjectOutputStream(
					new FileOutputStream(fBuff)
				);
			} catch(final IOException e) {
				LogUtil.failure(
					LOG, Level.ERROR, e, "Failed to open temporary file for output"
				);
			}
		}
		fBuffOut = fBuffOutTmp;
	}
	//
	@Override
	public final String toString() {
		return getThreadFactory().toString();
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Consumer implementation /////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private final static class DataItemOutPutTask<T>
	implements Runnable {
		//
		private final ObjectOutput fBuffOut;
		private final T dataItem;
		//
		private DataItemOutPutTask(final ObjectOutput fBuffOut, final T dataItem) {
			this.fBuffOut = fBuffOut;
			this.dataItem = dataItem;
		}
		//
		@Override
		public final void run() {
			if(fBuffOut != null) {
				try {
					fBuffOut.writeObject(dataItem);
				} catch(final IOException e) {
					LogUtil.failure(LOG, Level.WARN, e, "failed to write out the data item");
				}
			}
		}
	}
	//
	@Override
	public final void submit(T dataItem)
	throws IllegalStateException {
		//
		if(isShutdown() || fBuffOut == null) {
			throw new IllegalStateException();
		} else if(dataItem == null) {
			shutdown();
		} else {
			//
			final DataItemOutPutTask<T> outPutTask = new DataItemOutPutTask<>(fBuffOut, dataItem);
			boolean passed = false;
			int rejectCount = 0;
			while(
				!passed && rejectCount < retryCountMax && writtenDataItems.get() < maxCount &&
				!isShutdown()
			) {
				try {
					submit(outPutTask);
					writtenDataItems.incrementAndGet();
					passed = true;
				} catch(final RejectedExecutionException e) {
					rejectCount ++;
					try {
						Thread.sleep(rejectCount * retryDelayMilliSec);
					} catch(final InterruptedException ee) {
						break;
					}
				}
			}
			//
			if(!passed) {
				LOG.debug(
					LogUtil.ERR, "Data item \"{}\" has been rejected after {} tries",
					dataItem, rejectCount
				);
			}
		}
	}
	//
	@Override
	public final synchronized long getMaxCount() {
		return maxCount;
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Closeable implementation ////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final synchronized void close()
	throws IOException {
		if(fBuffOut != null) {
			//
			LOG.debug(LogUtil.MSG, "{}: output done, {} items", toString(), writtenDataItems.get());
			//
			shutdown();
			try {
				awaitTermination(
					RunTimeConfig.getContext().getRunReqTimeOutMilliSec(), TimeUnit.MILLISECONDS
				);
			} catch(final InterruptedException e) {
				LogUtil.failure(
					LOG, Level.DEBUG, e, "Interrupted while writing out the remaining data items"
				);
			} finally {
				final int droppedTaskCount = shutdownNow().size();
				LOG.debug(
					LogUtil.MSG, "{}: wrote {} data items, dropped {}", toString(),
					writtenDataItems.addAndGet(-droppedTaskCount), droppedTaskCount
				);
			}
			//
			fBuffOut.close();
			fBuffOut = null;
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Producer implementation /////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	private volatile Consumer<T> consumer = null;
	private final Thread producerThread = new Thread("tmpFileProducer") {
		@Override @SuppressWarnings("unchecked")
		public final void run() {
			if(TmpFileItemBuffer.super.isTerminated()) {
				LOG.debug(LogUtil.MSG, "{}: started", getThreadFactory().toString());
				//
				long
					availDataItems = writtenDataItems.get(),
					consumerMaxCount = Long.MAX_VALUE;
				try {
					consumerMaxCount = consumer.getMaxCount();
				} catch(final RemoteException e) {
					LogUtil.failure(LOG, Level.WARN, e, "Looks like network failure");
				}
				LOG.debug(
					LogUtil.MSG, "{}: {} available data items to read, while consumer limit is {}",
					getThreadFactory().toString(), availDataItems, consumerMaxCount
				);
				//
				if(fBuff == null) {
					LOG.warn(LogUtil.ERR, "No temporary file is available for producing");
				} else {
					T nextDataItem;
					try(
						final ObjectInput
							fBuffIn = new ObjectInputStream(new FileInputStream(fBuff))
					) {
						while(availDataItems -- > 0 && consumerMaxCount -- > 0) {
							nextDataItem = (T) fBuffIn.readObject();
							consumer.submit(nextDataItem);
							if(nextDataItem == null) {
								break;
							}
						}
						LOG.debug(LogUtil.MSG, "done producing");
					} catch(final RemoteException e) {
						LogUtil.failure(LOG, Level.DEBUG, e, "Failed to submit a data item");
					} catch(final IOException | ClassNotFoundException | ClassCastException e) {
						LogUtil.failure(LOG, Level.WARN, e, "Failed to read a data item");
					} catch(final InterruptedException e) {
						LOG.trace(LogUtil.ERR, "Interrupted during submit the data item");
					} catch(final RejectedExecutionException e) {
						LOG.debug(LogUtil.ERR, "Consumer rejected the data item");
					} finally {
						try {
							consumer.submit(null); // feed the poison
						} catch(final RemoteException e) {
							LogUtil.failure(LOG, Level.WARN, e, "Looks like network failure");
						} catch(final InterruptedException e) {
							LOG.trace(LogUtil.ERR, "Interrupted");
						} catch(final RejectedExecutionException e) {
							LOG.debug(LogUtil.ERR, "Consumer rejected the poison");
						} finally {
							consumer = null;
							if(fBuff.delete()) {
								LOG.debug(
									LogUtil.MSG, "File \"{}\" succesfully deleted",
									fBuff.getAbsolutePath()
								);
							} else {
								LOG.debug(
									LogUtil.ERR, "Failed to delete the file \"{}\"",
									fBuff.getAbsolutePath()
								);
							}
						}
					}
				}
			} else {
				LOG.warn(
					LogUtil.ERR,
					"Failed to start \"{}\" producing: illegal state: output isn't closed yet",
					getThreadFactory().toString()
				);
			}
		}
	};
	//
	@Override
	public final void setConsumer(Consumer<T> consumer) {
		this.consumer = consumer;
	}
	//
	@Override
	public final Consumer<T> getConsumer() {
		return consumer;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final void start() {
		producerThread.start();
	}
	//
	@Override
	public final void join()
	throws InterruptedException {
		producerThread.join();
	}
	//
	@Override
	public final void join(final long ms)
		throws InterruptedException {
		producerThread.join(ms);
	}
	//
	@Override
	public final synchronized void interrupt() {
		if(consumer != null) {
			try {
				consumer.submit(null); // feed the poison
			} catch(final RemoteException | InterruptedException | RejectedExecutionException e) {
				// ignore
			}
		}
		producerThread.interrupt();
		LOG.debug(LogUtil.MSG, "{}: interrupted", getThreadFactory().toString());
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
}