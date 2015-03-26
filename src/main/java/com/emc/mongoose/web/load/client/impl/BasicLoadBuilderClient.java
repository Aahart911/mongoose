package com.emc.mongoose.web.load.client.impl;
//
import com.emc.mongoose.base.load.LoadExecutor;
import com.emc.mongoose.base.load.client.impl.LoadBuilderClientBase;
import com.emc.mongoose.base.load.server.LoadBuilderSvc;
import com.emc.mongoose.util.logging.TraceLogger;
import com.emc.mongoose.util.remote.Service;
import com.emc.mongoose.web.api.WSRequestConfig;
import com.emc.mongoose.web.api.impl.WSRequestConfigBase;
import com.emc.mongoose.web.data.WSObject;
import com.emc.mongoose.web.data.impl.BasicWSObject;
import com.emc.mongoose.util.conf.RunTimeConfig;
import com.emc.mongoose.base.data.persist.FileProducer;
import com.emc.mongoose.util.logging.Markers;
import com.emc.mongoose.base.load.server.LoadSvc;
import com.emc.mongoose.util.remote.ServiceUtils;
//
import com.emc.mongoose.web.load.client.WSLoadBuilderClient;
import com.emc.mongoose.web.load.client.WSLoadClient;
import com.emc.mongoose.web.load.server.WSLoadBuilderSvc;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 Created by kurila on 08.05.14.
 */
public final class BasicLoadBuilderClient<T extends WSObject, U extends WSLoadClient<T>>
extends LoadBuilderClientBase<T, U>
implements WSLoadBuilderClient<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicLoadBuilderClient()
	throws IOException {
		super();
	}
	//
	public BasicLoadBuilderClient(final RunTimeConfig runTimeConfig)
	throws IOException {
		super(runTimeConfig);
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected WSRequestConfig<T> getDefaultRequestConfig() {
		return (WSRequestConfig<T>) WSRequestConfigBase.getInstance();
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected WSLoadBuilderSvc<T, U> resolve(final String serverAddr)
	throws IOException {
		WSLoadBuilderSvc<T, U> rlb;
		final Service remoteSvc = ServiceUtils.getRemoteSvc(
			"//" + serverAddr + '/' + getClass().getPackage().getName().replace("client", "server")
		);
		if(remoteSvc==null) {
			throw new IOException("No remote load builder was resolved from " + serverAddr);
		} else if(WSLoadBuilderSvc.class.isInstance(remoteSvc)) {
			rlb = WSLoadBuilderSvc.class.cast(remoteSvc);
		} else {
			throw new IOException(
				"Illegal class "+remoteSvc.getClass().getCanonicalName()+
					" of the instance resolved from "+serverAddr
			);
		}
		return rlb;
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final BasicLoadBuilderClient<T, U> setInputFile(final String listFile)
	throws RemoteException {
		if(listFile!=null) {
			try {
				srcProducer = (FileProducer<T>) new FileProducer<>(getMaxCount(), listFile, BasicWSObject.class);
				LOG.info(Markers.MSG, "Local data items will be read from file @ \"{}\"", listFile);
			} catch(final NoSuchMethodException | IOException e) {
				LOG.error(Markers.ERR, "Failure", e);
			}
		}
		return this;
	}
	//
	@Override  @SuppressWarnings("unchecked")
	public final U build()
	throws RemoteException {
		//
		WSLoadClient newLoadClient;
		//
		final Map<String, LoadSvc<T>> remoteLoadMap = new ConcurrentHashMap<>();
		final Map<String, JMXConnector> remoteJMXConnMap = new ConcurrentHashMap<>();
		//
		LoadBuilderSvc<T, U> nextBuilder;
		LoadSvc<T> nextLoad = null;
		//
		String svcJMXAddr;
		JMXServiceURL nextJMXURL;
		JMXConnector nextJMXConn;
		final int jmxImportPort = runTimeConfig.getRemoteImportPort();
		//
		for(final String addr : keySet()) {
			//
			nextBuilder = get(addr);
			nextBuilder.setRequestConfig(reqConf); // should upload req conf right before instancing
			nextLoad = (LoadSvc<T>) ServiceUtils.getRemoteSvc(
				String.format("//%s/%s", addr, nextBuilder.buildRemotely())
			);
			remoteLoadMap.put(addr, nextLoad);
			//
			nextJMXURL = null;
			try {
				svcJMXAddr = ServiceUtils.JMXRMI_URL_PREFIX + addr + ":" +
					Integer.toString(jmxImportPort) + ServiceUtils.JMXRMI_URL_PATH +
					Integer.toString(jmxImportPort);
				nextJMXURL = new JMXServiceURL(svcJMXAddr);
				LOG.debug(Markers.MSG, "Server JMX URL: {}", svcJMXAddr);
			} catch(final MalformedURLException e) {
				TraceLogger.failure(LOG, Level.ERROR, e, "Failed to generate JMX URL");
			}
			//
			nextJMXConn = null;
			if(nextJMXURL != null) {
				try {
					nextJMXConn = JMXConnectorFactory.connect(nextJMXURL, null);
				} catch(final IOException e) {
					TraceLogger.failure(
						LOG, Level.ERROR, e,
						String.format("Failed to connect to \"%s\" via JMX", nextJMXURL)
					);
				}
			}
			//
			if(nextJMXConn!=null) {
				remoteJMXConnMap.put(addr, nextJMXConn);
			}
			//
		}
		//
		newLoadClient = new BasicWSLoadClient<>(
			runTimeConfig, remoteLoadMap, remoteJMXConnMap, (WSRequestConfig<T>) reqConf,
			runTimeConfig.getDataCount()
		);
		LOG.debug(Markers.MSG, "Load client {} created", newLoadClient.getName());
		if(srcProducer != null && srcProducer.getConsumer() == null) {
			LOG.debug(
				Markers.MSG, "Append consumer {} for producer {}",
				newLoadClient.getName(), srcProducer.getName()
			);
			srcProducer.setConsumer(newLoadClient);
			srcProducer.start();
			srcProducer = null;
		}
		//
		return (U) newLoadClient;
	}
}