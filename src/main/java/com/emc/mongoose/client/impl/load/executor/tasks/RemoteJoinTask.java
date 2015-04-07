package com.emc.mongoose.client.impl.load.executor.tasks;
// mongoose-common.jar
import com.emc.mongoose.common.logging.LogUtil;
// mongoose-server-api.jar
import com.emc.mongoose.server.api.load.executor.LoadSvc;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
/**
 Created by kurila on 23.12.14.
 */
public final class RemoteJoinTask
implements Runnable {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final LoadSvc loadSvc;
	private final long timeOutMilliSec;
	//
	public RemoteJoinTask(final LoadSvc loadSvc, final long timeOutMilliSec) {
		this.loadSvc = loadSvc;
		this.timeOutMilliSec = timeOutMilliSec;
	}
	//
	@Override
	public final void run() {
		try {
			try {
				loadSvc.join(timeOutMilliSec);
			} catch(final InterruptedException e) {
				LOG.debug(
					LogUtil.MSG, "Remote join task for \"{}\" was interrupted", loadSvc.getName()
				);
			}
		} catch(final NoSuchObjectException e) {
			LogUtil.failure(LOG, Level.DEBUG, e, "Remote join failed, no such service");
		} catch(final RemoteException e) {
			LogUtil.failure(LOG, Level.WARN, e, "Remote join task failure");
		}
	}
}