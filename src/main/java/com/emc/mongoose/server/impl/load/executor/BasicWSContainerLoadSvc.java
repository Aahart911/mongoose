package com.emc.mongoose.server.impl.load.executor;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.Service;
import com.emc.mongoose.common.net.ServiceUtil;
//
import com.emc.mongoose.core.api.container.Container;
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.data.model.ItemDst;
import com.emc.mongoose.core.api.data.model.ItemSrc;
import com.emc.mongoose.core.api.io.req.WSRequestConfig;
//
import com.emc.mongoose.core.impl.load.executor.BasicWSContainerLoadExecutor;
//
import com.emc.mongoose.server.api.load.executor.WSContainerLoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
/**
 Created by kurila on 21.10.15.
 */
public class BasicWSContainerLoadSvc<T extends WSObject, C extends Container<T>>
extends BasicWSContainerLoadExecutor<T, C>
implements WSContainerLoadSvc<T, C> {
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicWSContainerLoadSvc(
		final RunTimeConfig runTimeConfig, final WSRequestConfig reqConfig, final String[] addrs,
		final int connPerNode, final int threadsPerNode,
		final ItemSrc<C> itemSrc, final long maxCount,
		final int manualTaskSleepMicroSecs, final float rateLimit
	) {
		super(
			runTimeConfig, reqConfig, addrs, connPerNode, threadsPerNode, itemSrc, maxCount,
			manualTaskSleepMicroSecs, rateLimit
		);
	}
	//
	@Override
	protected void closeActually()
		throws IOException {
		try {
			super.closeActually();
		} finally {
			// close the exposed network service, if any
			final Service svc = ServiceUtil.getLocalSvc(ServiceUtil.getLocalSvcName(getName()));
			if(svc == null) {
				LOG.debug(Markers.MSG, "The load was not exposed remotely");
			} else {
				LOG.debug(Markers.MSG, "The load was exposed remotely, removing the service");
				ServiceUtil.close(svc);
			}
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final void setItemDst(final ItemDst<C> itemDst) {
		LOG.debug(
			Markers.MSG, "Set consumer {} for {}, trying to resolve local service from the name",
			itemDst, getName()
		);
		try {
			if(itemDst instanceof Service) {
				final String remoteSvcName = ((Service) itemDst).getName();
				LOG.debug(Markers.MSG, "Name is {}", remoteSvcName);
				final Service localSvc = ServiceUtil.getLocalSvc(
					ServiceUtil.getLocalSvcName(remoteSvcName)
				);
				if(localSvc == null) {
					LOG.error(
						Markers.ERR, "Failed to get local service for name \"{}\"",
						remoteSvcName
					);
				} else {
					super.setItemDst((ItemDst<C>) localSvc);
					LOG.debug(
						Markers.MSG,
						"Successfully resolved local service and appended it as consumer"
					);
				}
			} else {
				super.setItemDst(itemDst);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "{}: looks like network failure", getName());
		}
	}
	// prevent output buffer consuming by the logger at the end of a chain
	@Override
	protected final void passDataItems()
	throws InterruptedException {
		if(consumer != null) {
			super.passDataItems();
		}
	}
	//
	@Override
	public final List<C> getProcessedItems()
		throws RemoteException {
		List<C> itemsBuff = null;
		try {
			itemsBuff = new ArrayList<>(batchSize);
			itemOutBuff.get(itemsBuff, batchSize);
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to get the buffered items");
		}
		return itemsBuff;
	}
	//
	@Override
	public final int getInstanceNum() {
		return instanceNum;
	}
	//
}