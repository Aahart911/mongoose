package com.emc.mongoose.storage.mock.impl.web.net;
// mongoose-common.jar
import com.emc.mongoose.common.concurrent.GroupThreadFactory;

import static com.emc.mongoose.common.conf.Constants.BUFF_SIZE_LO;

import com.emc.mongoose.common.concurrent.ThreadUtil;
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.io.IOWorker;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-storage-mock.jar
import com.emc.mongoose.storage.mock.api.StorageIOStats;
//
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.ListeningIOReactor;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
/**
 Created by kurila on 13.05.15.
 */
public final class BasicSocketEventDispatcher
extends DefaultHttpServerIODispatch
implements Runnable {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static GroupThreadFactory THREAD_GROUP = new GroupThreadFactory(
		"wsMockSocketEvtDispatcher", true
	);
	//
	private final ListeningIOReactor ioReactor;
	private final InetSocketAddress socketAddress;
	private final IOReactorConfig ioReactorConf;
	private final StorageIOStats ioStats;
	private final Thread executor;
	//
	public BasicSocketEventDispatcher(
		final BasicConfig appConfig,
		final HttpAsyncService protocolHandler, final int port,
		final NHttpConnectionFactory<DefaultNHttpServerConnection> connFactory,
		final StorageIOStats ioStats
	) throws IOReactorException {
		super(protocolHandler, connFactory);
		socketAddress = new InetSocketAddress(port);
		// set I/O reactor configuration
		final long timeOutMs = appConfig.getLoadLimitTimeUnit().toMillis(
			appConfig.getLoadLimitTimeValue()
		);
		int ioThreadCount = appConfig.getStorageMockWorkersPerSocket();
		if(ioThreadCount == 0) {
			ioThreadCount = ThreadUtil.getWorkerCount();
		}
		LOG.info(
			Markers.MSG, "Socket {}:{} will use {} I/O threads",
			socketAddress.getHostString(), socketAddress.getPort(), ioThreadCount
		);
		ioReactorConf = IOReactorConfig.custom()
			.setIoThreadCount(ioThreadCount)
			.setBacklogSize((int) appConfig.getSocketBindBackLogSize())
			.setInterestOpQueued(appConfig.getSocketInterestOpQueued())
			.setSelectInterval(appConfig.getSocketSelectInterval())
			.setShutdownGracePeriod(appConfig.getSocketTimeOut())
			.setSoKeepAlive(appConfig.getSocketKeepAliveFlag())
			.setSoLinger(appConfig.getSocketLinger())
			.setSoReuseAddress(appConfig.getSocketReuseAddrFlag())
			.setSoTimeout(appConfig.getSocketTimeOut())
			.setTcpNoDelay(appConfig.getSocketTCPNoDelayFlag())
			.setRcvBufSize(BUFF_SIZE_LO)
			.setSndBufSize(BUFF_SIZE_LO)
			.setConnectTimeout(
				timeOutMs > 0 && timeOutMs < Integer.MAX_VALUE ? (int) timeOutMs : Integer.MAX_VALUE
			)
			.build();
		// create the server-side I/O reactor
		this.ioStats = ioStats;
		final IOWorker.Factory ioWorkerFactory = new IOWorker.Factory(
			"ioReactor<" + socketAddress.getHostString() + ":" + socketAddress.getPort() + ">"
		);
		ioReactor = new DefaultListeningIOReactor(ioReactorConf, ioWorkerFactory);
		executor = THREAD_GROUP.newThread(this);

	}
	//
	public final void start() {
		executor.start();
	}
	//
	@Override
	public final void run() {
		try {
			// Listen of the given port
			ioReactor.listen(socketAddress);
			// Ready to go!
			ioReactor.execute(this);
		} catch (final InterruptedIOException e) {
			LOG.debug(Markers.MSG, "{}: interrupted", this);
		} catch (final IOReactorException e) {
			LogUtil.exception(LOG, Level.WARN, e, "{}: I/O reactor failure", this);
		} catch (final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "{}: I/O failure", this);
		} finally {
			try {
				close();
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.WARN, e, "{}: I/O failure during the shutdown", this);
			}
		}
	}
	//
	public final void join()
	throws InterruptedException {
		executor.join();
	}
	//
	public final void close()
	throws IOException {
		try {
			ioReactor.shutdown();
		} finally {
			executor.interrupt(); // just try
		}
	}
	//
	@Override
	public final String toString() {
		return BasicSocketEventDispatcher.class.getSimpleName() + ":" + socketAddress.getPort();
	}
	//
}
