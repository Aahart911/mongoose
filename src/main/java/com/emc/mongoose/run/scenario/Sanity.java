package com.emc.mongoose.run.scenario;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.common.net.ServiceUtil;
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.data.model.DataItemDst;
//
import com.emc.mongoose.core.impl.data.BasicWSObject;
import com.emc.mongoose.core.impl.data.model.BinFileItemDst;
import com.emc.mongoose.core.impl.data.model.CSVFileItemDst;
//
import com.emc.mongoose.core.impl.data.model.BlockingItemBuffer;
import com.emc.mongoose.core.impl.data.model.ListItemDst;
//
//
import com.emc.mongoose.server.api.load.builder.LoadBuilderSvc;
import com.emc.mongoose.server.api.load.executor.WSLoadSvc;
import com.emc.mongoose.server.impl.load.builder.BasicWSLoadBuilderSvc;
import com.emc.mongoose.storage.mock.impl.web.Cinderella;
//
import com.emc.mongoose.util.client.api.StorageClient;
import com.emc.mongoose.util.client.api.StorageClientBuilder;
import com.emc.mongoose.util.client.impl.BasicWSClientBuilder;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
/**
 Created by andrey on 22.06.15.
 */
public class Sanity
implements Runnable {
	//
	private final static short DEFAULT_NODE_COUNT = 4, DEFAULT_CONN_PER_NODE = 200;
	private final static long DEFAULT_DATA_SIZE = SizeUtil.toSize("10MB");
	private final static int DEFAULT_DATA_COUNT_MAX = 10000;
	public final static Logger LOG;
	static {
		LogUtil.init();
		LOG = LogManager.getLogger();
	}
	//
	private final StorageClient<WSObject> client;
	//
	public Sanity(final StorageClient<WSObject> client) {
		this.client = client;
	}
	//
	public void run() {
		try {
			// create new items
			final DataItemDst<WSObject> dataDstW = new ListItemDst<>(
				new ArrayList<WSObject>(DEFAULT_DATA_COUNT_MAX)
			);
			LOG.info(Markers.MSG, "Start writing");
			final long nWritten = client.write(
				null, dataDstW, DEFAULT_DATA_COUNT_MAX, DEFAULT_CONN_PER_NODE, DEFAULT_DATA_SIZE
			);
			LOG.info(Markers.MSG, "Written successfully {} items", nWritten);
			// update the created items
			LOG.info(Markers.MSG, "Start updating");
			final DataItemDst<WSObject> dataDstU = new BinFileItemDst<>();
			final long nUpdated = client.update(
				dataDstW.getDataItemSrc(), dataDstU, nWritten, DEFAULT_CONN_PER_NODE, 20
			);
			LOG.info(Markers.MSG, "Updated successfully {} items", nUpdated);
			// read and verify the updated items
			final DataItemDst<WSObject> dataDstR = new CSVFileItemDst<>(
				(Class<? extends WSObject>) BasicWSObject.class
			);
			final long nRead = client.read(
				dataDstU.getDataItemSrc(), dataDstR, nUpdated, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead);
			// variable-sized appending of the verified data items
			final DataItemDst<WSObject> dataDstA = new BlockingItemBuffer<>(
				new ArrayBlockingQueue<WSObject>(DEFAULT_DATA_COUNT_MAX)
			);
			final long nAppended = client.append(
				dataDstR.getDataItemSrc(), dataDstA, nRead, DEFAULT_CONN_PER_NODE,
				DEFAULT_DATA_SIZE, 3 * DEFAULT_DATA_SIZE, 1
			);
			LOG.info(Markers.MSG, "Appended successfully {} items", nAppended);
			// update again the appended data items
			final Path fileTmpItems0 = Files.createTempFile("reUpdatedItems", ".csv"); // do not delete on exit
			final DataItemDst<WSObject> dataDstU2 = new CSVFileItemDst<>(
				fileTmpItems0, (Class<? extends WSObject>) BasicWSObject.class
			);
			final long nUpdated2 = client.update(
				dataDstA.getDataItemSrc(), dataDstU2, nAppended, DEFAULT_CONN_PER_NODE, 20
			);
			LOG.info(Markers.MSG, "Updated again successfully {} items", nUpdated2);
			// read and verify the updated items again
			final long nRead2 = client.read(
				dataDstU2.getDataItemSrc(), null, nUpdated2, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead2);
			// recreate the items
			final DataItemDst<WSObject> dataDstW2 = new CSVFileItemDst<>(
				(Class<? extends WSObject>) BasicWSObject.class
			);
			final long nReWritten = client.write(
				dataDstW.getDataItemSrc(), dataDstW2, nWritten, DEFAULT_CONN_PER_NODE, DEFAULT_DATA_SIZE
			);
			LOG.info(Markers.MSG, "Rewritten successfully {} items", nReWritten);
			// read and verify the rewritten data items
			final long nRead3 = client.read(
				dataDstW2.getDataItemSrc(), null, nWritten, DEFAULT_CONN_PER_NODE, true
			);
			LOG.info(Markers.MSG, "Read and verified successfully {} items", nRead3);
			// delete all created data items
			final long nDeleted = client.delete(
				dataDstW.getDataItemSrc(), null, nWritten, DEFAULT_CONN_PER_NODE
			);
			LOG.info(Markers.MSG, "Deleted successfully {} items", nDeleted);
		} catch(final Exception e) {
			e.printStackTrace(System.err);
			LogUtil.exception(LOG, Level.ERROR, e, "Sanity failure");
		}
	}
	//
	public static void main(final String... args)
	throws IOException, InterruptedException {
		//
		RunTimeConfig.initContext();
		final RunTimeConfig rtConfig = RunTimeConfig.getContext();
		//
		rtConfig.set(RunTimeConfig.KEY_STORAGE_MOCK_CAPACITY, DEFAULT_DATA_COUNT_MAX);
		rtConfig.set(RunTimeConfig.KEY_STORAGE_MOCK_HEAD_COUNT, DEFAULT_NODE_COUNT);
		//rtConfig.set(RunTimeConfig.KEY_LOAD_METRICS_PERIOD_SEC, 0);
		final Thread wsMockThread = new Thread(
			new Cinderella(RunTimeConfig.getContext()), "wsMock"
		);
		wsMockThread.setDaemon(true);
		wsMockThread.start();
		//
		rtConfig.set(RunTimeConfig.KEY_LOAD_METRICS_PERIOD_SEC, 10);
		final StorageClientBuilder<WSObject, StorageClient<WSObject>>
			clientBuilder = new BasicWSClientBuilder<>();
		final String storageNodes[] = new String[DEFAULT_NODE_COUNT];
		for(int i = 0; i < DEFAULT_NODE_COUNT; i++) {
			storageNodes[i] = "127.0.0.1:" + (9020 + i);
		}
		clientBuilder
			.setNodes(storageNodes)
			.setAuth("wuser1@sanity.local", "H1jTDL869wgZapHsylVcSYTx3aM7NxVABy8h017Z")
			.setLimitCount(DEFAULT_DATA_COUNT_MAX)
			.setLimitTime(0, TimeUnit.SECONDS)
			.setLimitRate(10000);
		// standalone
		try(final StorageClient<WSObject> client = clientBuilder.build()) {
			final Thread sanityThread1 = new Thread(new Sanity(client), "sanityStandalone");
			sanityThread1.start();
			LOG.info(Markers.MSG, "Standalone sanity started");
			sanityThread1.join();
			LOG.info(Markers.MSG, "Standalone sanity finished");
		}
		// distributed mode
		rtConfig.set(RunTimeConfig.KEY_REMOTE_SERVE_JMX, true);
		ServiceUtil.init();
		//
		try(
			final LoadBuilderSvc<WSObject, WSLoadSvc<WSObject>>
				loadSvcBuilder = new BasicWSLoadBuilderSvc<>(rtConfig);
		) {
			loadSvcBuilder.start();
			TimeUnit.SECONDS.sleep(1);
			rtConfig.set(RunTimeConfig.KEY_REMOTE_PORT_MONITOR, 1299);
			try(
				final StorageClient<WSObject> client = clientBuilder
					.setClientMode(new String[] {ServiceUtil.getHostAddr()})
					.build();
			) {
				final Thread sanityThread2 = new Thread(new Sanity(client), "sanityDistributed");
				sanityThread2.start();
				LOG.info(Markers.MSG, "Distributed sanity started");
				sanityThread2.join();
				LOG.info(Markers.MSG, "Distributed sanity finished");
			}
		}
		//
		ServiceUtil.shutdown();
		// finish
		wsMockThread.interrupt();
		LOG.info(Markers.MSG, "Storage mock stopped");
		LOG.info(Markers.MSG, "Sanity done");
		//
		LogUtil.shutdown();
	}
}