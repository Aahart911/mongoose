package com.emc.mongoose.core.impl.load.model;
//
import static com.emc.mongoose.common.conf.RunTimeConfig.getContext;
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataItemOutput;
import com.emc.mongoose.core.api.load.model.Consumer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
/**
 Created by kurila on 19.06.15.
 */
public class DataItemOutputConsumer<T extends DataItem>
extends AsyncConsumerBase<T>
implements Consumer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected final DataItemOutput<T> itemOut;
	//
	public DataItemOutputConsumer(final DataItemOutput<T> itemOut) {
		super(
			Long.MAX_VALUE,
			getContext().getLoadLimitTimeUnit().toMillis(
				getContext().getLoadLimitTimeValue()
			),
			getContext().getTasksMaxQueueSize(), getContext().getBatchSize()
		);
		this.itemOut = itemOut;
	}
	//
	@Override
	protected void feedSequentially(final T dataItem)
	throws InterruptedException, RemoteException {
		try {
			itemOut.write(dataItem);
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to write the data item");
		}
	}
	//
	@Override
	protected void feedSequentiallyAll(final List<T> dataItems) {
		try {
			itemOut.write(dataItems);
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to perform bulk data items write");
		}
	}
	//
	@Override
	public void close()
	throws IOException {
		// stop consuming
		shutdown();
		// wait for the queue processing is done
		try {
			join();
		} catch(final InterruptedException e) {
			LogUtil.exception(LOG, Level.DEBUG, e, "Unexpected interruption while closing");
		}
		// close
		try {
			itemOut.close();
		} finally {
			super.close();
		}
	}
}
