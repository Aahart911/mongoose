package com.emc.mongoose.client.impl.load.executor.tasks;
//
import com.emc.mongoose.common.collections.InstancePool;
import com.emc.mongoose.common.collections.Reusable;
import com.emc.mongoose.common.collections.ReusableList;
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.data.DataItem;
//
import com.emc.mongoose.server.api.load.executor.LoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.RemoteException;
import java.util.List;
/**
 Created by andrey on 22.05.15.
 */
public final class RemoteSubmitTask<T extends DataItem>
implements Runnable, Reusable<RemoteSubmitTask> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static InstancePool<RemoteSubmitTask>
		INSTANCE_POOL = new InstancePool<>(RemoteSubmitTask.class);
	//
	@SuppressWarnings("unchecked")
	public static <T extends DataItem> RemoteSubmitTask<T> getInstance(
		final LoadSvc<T> loadSvc, final T dataItem
	) {
		return INSTANCE_POOL.take(loadSvc, dataItem);
	}
	//
	@SuppressWarnings("unchecked")
	public static <T extends DataItem> RemoteSubmitTask<T> getInstance(
		final LoadSvc<T> loadSvc, final ReusableList<T> dataItems
	) {
		return INSTANCE_POOL.take(loadSvc, dataItems);
	}
	//
	private LoadSvc<T> loadSvc = null;
	private T dataItem = null;
	private ReusableList<T> dataItems = null;
	//
	@Override @SuppressWarnings("unchecked")
	public final RemoteSubmitTask reuse(final Object... args)
		throws IllegalArgumentException, IllegalStateException {
		if(args != null) {
			if(args.length > 0) {
				loadSvc = (LoadSvc<T>) args[0];
			}
			if(args.length > 1) {
				if(List.class.isInstance(args[1])) {
					dataItems = (ReusableList<T>) args[1];
					dataItem = null;
				} else {
					dataItems = null;
					dataItem = (T) args[1];
				}
			}
		}
		return this;
	}
	//
	@Override
	public final void release() {
		dataItems.release();
		INSTANCE_POOL.release(this);
	}
	//
	@Override
	public final
	void run() {
		try {
			if(dataItem != null) {
				loadSvc.feed(dataItem);
			} else if(dataItems != null) {
				if(dataItems.size() > 0) {
					loadSvc.feedAll(dataItems);
				}
			} else {
				LOG.debug(Markers.ERR, "Empty remote feeding task");
			}
		} catch(final InterruptedException ignored) {
		} catch(RemoteException e){
			LogUtil.exception(LOG, Level.WARN, e, "Failed to feed the data item {}", dataItem);
		} finally {
			release();
		}
	}
}
