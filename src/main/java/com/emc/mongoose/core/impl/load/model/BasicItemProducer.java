package com.emc.mongoose.core.impl.load.model;
//
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.item.base.Item;
import com.emc.mongoose.core.api.item.base.ItemDst;
import com.emc.mongoose.core.api.item.base.ItemSrc;
import com.emc.mongoose.core.api.load.model.ItemProducer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 Created by kurila on 19.06.15.
 */
public class BasicItemProducer<T extends Item>
extends Thread
implements ItemProducer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final ConcurrentHashMap<String, T> uniqueItems;
	protected final ItemSrc<T> itemSrc;
	protected final long maxCount;
	protected final boolean isCircular;
	protected final boolean isShuffling;
	protected final long tgtNanoTime;
	protected final int batchSize;
	protected volatile ItemDst<T> itemDst = null;
	protected long skipCount;
	protected T lastDataItem;
	protected int maxItemQueueSize;
	//
	protected volatile boolean areAllItemsProduced = false;
	protected volatile long producedItemsCount = 0;
	//
	protected BasicItemProducer(
		final ItemSrc<T> itemSrc, final long maxCount, final int batchSize,
		final boolean isCircular, final boolean isShuffling, final int maxItemQueueSize,
	    final float rateLimit
	) {
		this(
			itemSrc, maxCount, batchSize, isCircular, isShuffling, maxItemQueueSize, rateLimit,
			0, null
		);
	}
	//
	private BasicItemProducer(
		final ItemSrc<T> itemSrc, final long maxCount, final int batchSize,
		final boolean isCircular, final boolean isShuffling, final int maxItemQueueSize,
		final float rateLimit,
		final long skipCount, final T lastDataItem
	) {
		this.itemSrc = itemSrc;
		this.maxCount = maxCount - skipCount;
		this.batchSize = batchSize;
		this.skipCount = skipCount;
		this.lastDataItem = lastDataItem;
		this.isCircular = isCircular;
		this.isShuffling = isShuffling;
		this.maxItemQueueSize = maxItemQueueSize;
		this.tgtNanoTime = rateLimit > 0 && !Float.isInfinite(rateLimit) && !Float.isNaN(rateLimit) ?
			(long) (TimeUnit.SECONDS.toNanos(1) / rateLimit) :
			0;
		this.uniqueItems = new ConcurrentHashMap<>(maxItemQueueSize);
	}
	//
	@Override
	public void setSkipCount(final long itemsCount) {
		this.skipCount = itemsCount;
	}
	//
	@Override
	public void setLastItem(final T dataItem) {
		this.lastDataItem = dataItem;
	}
	//
	@Override
	public void setItemDst(final ItemDst<T> itemDst)
	throws RemoteException {
		this.itemDst = itemDst;
	}
	//
	@Override
	public ItemSrc<T> getItemSrc()
	throws RemoteException {
		return itemSrc;
	}
	//
	@Override
	public void reset() {
		if(itemSrc != null) {
			try {
				itemSrc.reset();
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to reset data item input");
			}
		}
	}
	//
	@Override
	public final void run() {
		runActually();
	}
	//
	protected void runActually() {
		//
		if(itemSrc == null) {
			LOG.debug(Markers.MSG, "No item source for the producing, exiting");
			return;
		}
		int n = 0, m = 0;
		long nt = System.nanoTime(); // LOAD RATE LIMIT FEATURE
		try {
			List<T> buff;
			while(maxCount > producedItemsCount && !isInterrupted) {
				try {
					buff = new ArrayList<>(batchSize);
					n = (int) Math.min(itemSrc.get(buff, batchSize), maxCount - producedItemsCount);
					if(isShuffling) {
						Collections.shuffle(buff);
					}
					if(isInterrupted) {
						break;
					}
					if(n > 0) {
						if(tgtNanoTime > 0) {
							// LOAD RATE LIMIT FEATURE:
							// sleep the calculated time in order to match the target rate
							nt = tgtNanoTime * n - System.nanoTime() + nt;
							if(nt > 0) {
								TimeUnit.NANOSECONDS.sleep(nt);
							}
							nt = System.nanoTime(); // mark the current time again
						}
						//
						for(m = 0; m < n && !isInterrupted; ) {
							m += itemDst.put(buff, m, n);
							LockSupport.parkNanos(1);
						}
						producedItemsCount += n;
					} else {
						if(isInterrupted) {
							break;
						}
					}
					// CIRCULARITY FEATURE:
					// produce only <maxItemQueueSize> items in order to make it possible to enqueue
					// them infinitely
					if(isCircular && producedItemsCount >= maxItemQueueSize) {
						break;
					}
				} catch(
					final EOFException | InterruptedException | ClosedByInterruptException |
					IllegalStateException e
				) {
					break;
				} catch(final IOException e) {
					LogUtil.exception(
						LOG, Level.DEBUG, e, "Failed to transfer the data items, count = {}, " +
						"batch size = {}, batch offset = {}", producedItemsCount, n, m
					);
				}
			}
		} finally {
			LOG.debug(
				Markers.MSG, "{}: produced {} items from \"{}\" for the \"{}\"",
				getName(), producedItemsCount, itemSrc, itemDst
			);
			try {
				itemSrc.close();
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to close the item source \"{}\"", itemSrc
				);
			}
		}
	}
	//
	protected void skipIfNecessary() {
		if(skipCount > 0) {
			try {
				itemSrc.setLastItem(lastDataItem);
				itemSrc.skip(skipCount);
			} catch (final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "Failed to skip {} items", skipCount);
			}
		}
	}
	//
	private volatile boolean isInterrupted = false;
	//
	@Override
	public void interrupt()
	throws IllegalStateException {
		isInterrupted = true;
	}
}
