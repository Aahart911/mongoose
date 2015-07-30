package com.emc.mongoose.core.impl.load.executor;
// mongoose-common.jar
import com.emc.mongoose.common.concurrent.GroupThreadFactory;
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.http.request.CustomizedApacheHttpAsyncRequester;
import com.emc.mongoose.common.net.http.request.SharedHeadersAdder;
import com.emc.mongoose.common.net.http.request.HostHeaderSetter;
import com.emc.mongoose.common.log.LogUtil;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.api.io.task.WSIOTask;
import com.emc.mongoose.core.api.io.req.conf.WSRequestConfig;
import com.emc.mongoose.core.api.load.executor.WSLoadExecutor;
// mongoose-core-impl.jar
import com.emc.mongoose.core.impl.io.task.BasicWSIOTask;
import com.emc.mongoose.core.impl.data.BasicWSObject;
import com.emc.mongoose.core.impl.load.tasks.HttpClientRunTask;
//
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpHost;
import org.apache.http.concurrent.BasicFuture;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.message.HeaderGroup;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestUserAgent;
//
import org.apache.http.nio.util.DirectByteBufferAllocator;
import org.apache.http.impl.nio.pool.BasicNIOConnPool;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.DefaultHttpClientIODispatch;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.pool.NIOConnFactory;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 02.12.14.
 */
public class BasicWSLoadExecutor<T extends WSObject>
extends ObjectLoadExecutorBase<T>
implements WSLoadExecutor<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	@SuppressWarnings("FieldCanBeLocal")
	private final HttpProcessor httpProcessor;
	private final CustomizedApacheHttpAsyncRequester client;
	private final ConnectingIOReactor ioReactor;
	private final BasicNIOConnPool connPool;
	private final Thread clientDaemon;
	private final WSRequestConfig<T> wsReqConfCopy;
	//
	@SuppressWarnings("unchecked")
	public BasicWSLoadExecutor(
		final RunTimeConfig runTimeConfig, final WSRequestConfig<T> reqConfig, final String[] addrs,
		final int connCountPerNode, final String listFile, final long maxCount,
		final long sizeMin, final long sizeMax, final float sizeBias, final float rateLimit,
		final int countUpdPerReq
	) {
		super(
			(Class<T>) BasicWSObject.class,
			runTimeConfig, reqConfig, addrs, connCountPerNode, listFile, maxCount,
			sizeMin, sizeMax, sizeBias, rateLimit, countUpdPerReq
		);
		wsReqConfCopy = (WSRequestConfig<T>) reqConfigCopy;
		//
		final int totalConnCount = connCountPerNode * storageNodeCount;
		final HeaderGroup sharedHeaders = wsReqConfCopy.getSharedHeaders();
		final String userAgent = runTimeConfig.getRunName() + "/" + runTimeConfig.getRunVersion();
		//
		httpProcessor = HttpProcessorBuilder
			.create()
			.add(new SharedHeadersAdder(sharedHeaders))
			.add(new HostHeaderSetter())
			.add(new RequestConnControl())
			.add(new RequestUserAgent(userAgent))
			//.add(new RequestExpectContinue(true))
			.add(new RequestContent(false))
			.build();
		client = new CustomizedApacheHttpAsyncRequester(
			httpProcessor, DefaultConnectionReuseStrategy.INSTANCE,
			new ExceptionLogger() {
				@Override
				public final void log(final Exception e) {
					LogUtil.exception(LOG, Level.DEBUG, e, "HTTP client internal failure");
				}
			}
		);
		//
		final RunTimeConfig thrLocalConfig = RunTimeConfig.getContext();
		final int buffSize = wsReqConfCopy.getBuffSize();
		final long timeOutMs = runTimeConfig.getLoadLimitTimeUnit().toMillis(
			runTimeConfig.getLoadLimitTimeValue()
		);
		final IOReactorConfig.Builder ioReactorConfigBuilder = IOReactorConfig
			.custom()
			.setIoThreadCount(totalConnCount)
			.setBacklogSize((int) thrLocalConfig.getSocketBindBackLogSize())
			.setInterestOpQueued(thrLocalConfig.getSocketInterestOpQueued())
			.setSelectInterval(thrLocalConfig.getSocketSelectInterval())
			.setShutdownGracePeriod(thrLocalConfig.getSocketTimeOut())
			.setSoKeepAlive(thrLocalConfig.getSocketKeepAliveFlag())
			.setSoLinger(thrLocalConfig.getSocketLinger())
			.setSoReuseAddress(thrLocalConfig.getSocketReuseAddrFlag())
			.setSoTimeout(thrLocalConfig.getSocketTimeOut())
			.setTcpNoDelay(thrLocalConfig.getSocketTCPNoDelayFlag())
			.setRcvBufSize(IOTask.Type.READ.equals(loadType) ? buffSize : Constants.BUFF_SIZE_LO)
			.setSndBufSize(IOTask.Type.READ.equals(loadType) ? Constants.BUFF_SIZE_LO : buffSize)
			.setConnectTimeout(
				timeOutMs > 0 && timeOutMs < Integer.MAX_VALUE ? (int) timeOutMs : Integer.MAX_VALUE
			);
		//
		final NHttpClientEventHandler reqExecutor = new HttpAsyncRequestExecutor();
		//
		final ConnectionConfig connConfig = ConnectionConfig
			.custom()
			.setBufferSize(buffSize)
			.setFragmentSizeHint(0)
			.build();
		final IOEventDispatch ioEventDispatch = new DefaultHttpClientIODispatch(
			reqExecutor, connConfig
		);
		//
		try {
			ioReactor = new DefaultConnectingIOReactor(
				ioReactorConfigBuilder.build(), new GroupThreadFactory(getName())
			);
		} catch(final IOReactorException e) {
			throw new IllegalStateException("Failed to build the I/O reactor", e);
		}
		//
		final NIOConnFactory<HttpHost, NHttpClientConnection>
			connFactory = new BasicNIOConnFactory(
				null, null, null, null,
				DirectByteBufferAllocator.INSTANCE, connConfig
			);
		//
		connPool = new BasicNIOConnPool(
			ioReactor, connFactory,
			timeOutMs > 0 && timeOutMs < Integer.MAX_VALUE ? (int) timeOutMs : Integer.MAX_VALUE
		);
		connPool.setMaxTotal(totalConnCount);
		connPool.setDefaultMaxPerRoute(connCountPerNode);
		//
		clientDaemon = new Thread(
			new HttpClientRunTask(ioEventDispatch, ioReactor), "clientDaemon<" + getName() + ">"
		);
	}
	//
	@Override
	public void start() {
		if(clientDaemon == null) {
			LOG.debug(Markers.ERR, "Not starting web load client due to initialization failures");
		} else {
			clientDaemon.start();
			super.start();
		}
	}
	//
	@Override
	public void close()
	throws IOException {
		try {
			super.close();
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Closing failure");
		} finally {
			clientDaemon.interrupt();
			LOG.debug(
				Markers.MSG, "Web storage client daemon \"{}\" interrupted", clientDaemon
			);
			if(connPool != null) {
				connPool.closeExpired();
				LOG.debug(Markers.MSG, "Closed expired (if any) connections in the pool");
				try {
					connPool.closeIdle(1, TimeUnit.MILLISECONDS);
					LOG.debug(Markers.MSG, "Closed idle connections (if any) in the pool");
				} finally {
					try {
						connPool.shutdown(1);
						LOG.debug(Markers.MSG, "Connection pool has been shut down");
					} catch(final IOException e) {
						LogUtil.exception(
							LOG, Level.WARN, e, "Connection pool shutdown failure"
						);
					}
				}
			}
			//
			ioReactor.shutdown(1);
			LOG.debug(Markers.MSG, "I/O reactor has been shut down");
			// dispose the I/O tasks pool
			if(BasicWSIOTask.INSTANCE_POOL_MAP.containsKey(wsReqConfCopy)) {
				BasicWSIOTask.INSTANCE_POOL_MAP.put(wsReqConfCopy, null);
			}
		}
	}
	//
	private final FutureCallback<WSIOTask<T>> taskCallback = new FutureCallback<WSIOTask<T>>() {
		//
		@Override
		public final void completed(final WSIOTask<T> task) {
			task.complete();
			handleResult(task);
		}
		//
		@Override
		public final void failed(final Exception e) {
			if(!reqConfigCopy.isClosed()) {
				LogUtil.exception(LOG, Level.DEBUG, e, "{}: I/O task failure", hashCode());
			}
		}
		//
		@Override
		public final void cancelled() {
			LOG.debug(Markers.MSG, "{}: I/O task canceled", hashCode());
		}
	};
	//
	@Override @SuppressWarnings("unchecked")
	public final Future<IOTask<T>> submitRequest(final IOTask<T> ioTask)
	throws RejectedExecutionException {
		//
		if(connPool.isShutdown()) {
			throw new RejectedExecutionException("Connection pool is shut down");
		}
		//
		final WSIOTask<T> wsTask = (WSIOTask<T>) ioTask;
		final BasicFuture<WSIOTask<T>> futureResult = new BasicFuture<>(taskCallback);
		try {
			connPool.lease(
				wsTask.getTarget(), null,
				client.getConnRequestCallback(
					futureResult, wsTask, wsTask, connPool, wsTask
				)
			);
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "I/O task #{} has been submitted for execution: {}",
					wsTask.hashCode(), futureResult
				);
			}
		} catch(final Exception e) {
			throw new RejectedExecutionException(e);
		}
		return (Future) futureResult;
	}
	//
	private final FutureCallback<List<WSIOTask<T>>> tasksCallback = new FutureCallback<List<WSIOTask<T>>>() {
		//
		@Override
		public final void completed(final List<WSIOTask<T>> tasks) {
			for(final WSIOTask<T> task : tasks) {
				taskCallback.completed(task);
			}
		}
		//
		@Override
		public final void failed(final Exception e) {
			if(!reqConfigCopy.isClosed()) {
				LogUtil.exception(LOG, Level.DEBUG, e, "{}: batch I/O tasks failure", hashCode());
			}
		}
		//
		@Override
		public final void cancelled() {
			LOG.debug(Markers.MSG, "{}: batch of I/O tasks canceled", hashCode());
		}
	};
	//
	@Override
	public final Future<List<IOTask<T>>> submitRequests(final List<IOTask<T>> ioTasksBuffer)
	throws RejectedExecutionException {
		//
		if(connPool.isShutdown()) {
			throw new RejectedExecutionException("Connection pool is shut down");
		}
		//
		final List<WSIOTask<T>> wsTasks = (List) ioTasksBuffer;
		final WSIOTask<T> anyTask = wsTasks.get(0);
		final BasicFuture<List<WSIOTask<T>>> futureResults = new BasicFuture<>(tasksCallback);
		try {
			connPool.lease(
				anyTask.getTarget(), null,
				client.getConnPipelinedRequestCallback(
					futureResults, wsTasks, wsTasks, connPool, anyTask
				)
			);
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "{} I/O tasks have been submitted for execution to {}",
					wsTasks.size(), anyTask.getTarget()
				);
			}
		} catch(final Exception e) {
			throw new RejectedExecutionException(e);
		}
		return (Future) futureResults;
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected IOTask<T> getIOTask(final T dataItem, final String nextNodeAddr) {
		return BasicWSIOTask.getInstance(dataItem, wsReqConfCopy, nextNodeAddr);
	}
	//
	@Override
	protected void getIOTasks(
		final List<IOTask<T>> taskBuff, final List<T> dataItems, final int maxCount,
		final String nextNodeAddr
	) {
		BasicWSIOTask.getInstances(taskBuff, dataItems, maxCount, wsReqConfCopy, nextNodeAddr);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Balancing based on the connection pool stats
	////////////////////////////////////////////////////////////////////////////////////////////////
	/*private volatile Set<HttpHost> routes = null;
	private final static ThreadLocal<Map<HttpHost, String>>
		THREAD_CACHED_REVERSE_NODE_MAP = new ThreadLocal<>();
	@Override
	protected final String getNextNode() {
		HttpHost nodeHost = null;
		// connPool.getRoutes() is quite expensive, so reuse the routes set
		if(routes == null || routes.size() < storageNodeCount) {
			routes = connPool.getRoutes();
		} else {
			// select the route having the max count of the free connections in the pool
			// TODO think how to not to invoke connPool.getStats(HttpHost route)
			int maxConnCount = -1, nextConnCount;
			for(final HttpHost nextRoute : routes) {
				nextConnCount = connPool.getStats(nextRoute).getAvailable();
				if(nextConnCount > maxConnCount) {
					maxConnCount = nextConnCount;
					nodeHost = nextRoute;
				}
			}
		}
		//
		String nodeAddr;
		if(nodeHost == null) { // fallback
			nodeAddr = super.getNextNode();
		} else {
			Map<HttpHost, String> cachedReverseNodeMap = THREAD_CACHED_REVERSE_NODE_MAP.get();
			if(cachedReverseNodeMap == null) {
				cachedReverseNodeMap = new HashMap<>();
				THREAD_CACHED_REVERSE_NODE_MAP.set(cachedReverseNodeMap);
			}
			nodeAddr = cachedReverseNodeMap.get(nodeHost);
			if(nodeAddr == null) {
				nodeAddr = nodeHost.toHostString();
				cachedReverseNodeMap.put(nodeHost, nodeAddr);
			}
		}
		return nodeAddr;
	}*/
}
