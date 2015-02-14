package com.emc.mongoose.web.api.impl.provider.atmos;
//
import com.emc.mongoose.base.load.Consumer;
import com.emc.mongoose.base.load.Producer;
import com.emc.mongoose.util.logging.Markers;
import com.emc.mongoose.util.logging.TraceLogger;
import com.emc.mongoose.web.api.WSIOTask;
import com.emc.mongoose.web.data.WSObject;
import com.emc.mongoose.web.load.WSLoadExecutor;
//
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import org.xml.sax.SAXException;
//
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
/**
 Created by kurila on 23.01.15.
 */
public class SubTenantProducer<T extends WSObject, U extends WSObject>
extends Thread
implements Producer<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private volatile Consumer<T> consumer = null;
	private final SubTenant<T> subTenant;
	private final Constructor<T> dataConstructor;
	private final long maxCount;
	private final WSLoadExecutor<T> wsClient;
	//
	@SuppressWarnings("unchecked")
	public SubTenantProducer(
		final SubTenant<T> subTenant, final Class<U> dataCls, final long maxCount,
		final WSLoadExecutor<T> wsClient
	) throws ClassCastException, NoSuchMethodException {
		super("subtenant-" + subTenant + "-producer");
		this.subTenant = subTenant;
		this.dataConstructor = (Constructor<T>) dataCls.getConstructor(
			String.class, Long.class, Long.class
		);
		this.maxCount = maxCount > 0 ? maxCount : Long.MAX_VALUE;
		this.wsClient = wsClient;
	}
	//
	@Override
	public final void setConsumer(final Consumer<T> consumer) {
		this.consumer = consumer;
	}
	//
	@Override
	public final Consumer<T> getConsumer() {
		return consumer;
	}
	//
	@Override
	public final void run() {
		try {
			final HttpResponse httpResp = subTenant.execute(wsClient, WSIOTask.HTTPMethod.GET);
			if(httpResp != null) {
				final StatusLine statusLine = httpResp.getStatusLine();
				if(statusLine==null) {
					LOG.warn(Markers.MSG, "No response status");
				} else {
					final int statusCode = statusLine.getStatusCode();
					if(statusCode == HttpStatus.SC_OK) {
						final HttpEntity respEntity = httpResp.getEntity();
						final String respContentType = respEntity.getContentType().getValue();
						if(ContentType.APPLICATION_XML.getMimeType().equals(respContentType)) {
							try {
								final SAXParser parser = SAXParserFactory
									.newInstance().newSAXParser();
								try(final InputStream in = respEntity.getContent()) {
									parser.parse(
										in,
										new SubTenantListHandler<>(
											consumer, dataConstructor, maxCount
										)
									);
								} catch(final SAXException e) {
									TraceLogger.failure(LOG, Level.WARN, e, "Failed to parse");
								}
							} catch(final ParserConfigurationException | SAXException e) {
								TraceLogger.failure(
									LOG, Level.ERROR, e, "Failed to create SAX parser"
								);
							}
						} else {
							LOG.warn(
								Markers.MSG, "Unexpected response content type: \"{}\"",
								respContentType
							);
						}
					} else {
						final String statusMsg = statusLine.getReasonPhrase();
						LOG.debug(
							Markers.MSG, "Listing subtenant \"{}\" response: {}/{}",
							subTenant, statusCode, statusMsg
						);
					}
				}
				EntityUtils.consumeQuietly(httpResp.getEntity());
			}
		} catch(final IOException e) {
			TraceLogger.failure(
				LOG, Level.ERROR, e,
				String.format("Failed to list the subtenant \"%s\"", subTenant)
			);
		}
	}
	//
}