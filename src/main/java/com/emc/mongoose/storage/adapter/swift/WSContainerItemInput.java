package com.emc.mongoose.storage.adapter.swift;
//
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.data.DataObject;
import com.emc.mongoose.core.api.data.WSObject;
//
import com.emc.mongoose.core.api.io.req.MutableWSRequest;
import com.emc.mongoose.core.api.io.req.conf.WSRequestConfig;
//
import com.emc.mongoose.core.impl.data.GenericContainerItemInputBase;
//
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
//
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ListIterator;
/**
 Created by kurila on 03.07.15.
 */
public class WSContainerItemInput<T extends WSObject>
extends GenericContainerItemInputBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static JsonFactory JSON_FACTORY = new JsonFactory();
	private final static String KEY_SIZE = "bytes", KEY_ID = "name";
	//
	private boolean isInsideObjectToken = false;
	private String lastId = null;
	private long lastSize = -1, offset, count = 0;
	//
	public WSContainerItemInput(
		final WSContainerImpl<T> container, final String nodeAddr, final Class<T> itemCls
	) throws IllegalStateException {
		super(container, nodeAddr, itemCls);
	}
	//
	@Override
	protected final ListIterator<T> getNextPageIterator()
	throws EOFException, IOException {
		// execute the request
		final HttpResponse resp = WSContainerImpl.class.cast(container).execute(
			nodeAddr, MutableWSRequest.HTTPMethod.GET, lastId, WSRequestConfig.PAGE_SIZE
		);
		// response validation
		if(resp == null) {
			throw new IOException("No HTTP response is returned");
		}
		final StatusLine status = resp.getStatusLine();
		if(status == null) {
			throw new IOException("Invalid HTTP response: " + resp);
		}
		final int statusCode = status.getStatusCode();
		if(statusCode < 200 || statusCode > 300) {
			throw new IOException(
				"Listing container \"" + container + "\" response: " + status
			);
		}
		final HttpEntity respEntity = resp.getEntity();
		if(respEntity == null) {
			throw new IOException("No HTTP entity in the response: " + resp);
		}
		final String respContentType = respEntity.getContentType().getValue();
		if(!respContentType.toLowerCase().contains("json")) {
			LOG.warn(
				Markers.ERR, "Unexpected response content type: \"{}\"", respContentType
			);
		}
		// parse the response content
		try(final InputStream in = respEntity.getContent()) {
			final long lastTimeCount = count;
			handleJsonInputStream(in);
			LOG.info("Listed {} items the last time", count - lastTimeCount);
		}
		//
		return listPageBuffer.listIterator();
	}
	//
	@Override
	public final void reset()
	throws IOException {
		super.reset();
		lastId = null;
	}
	//
	private void handleJsonInputStream(final InputStream in)
	throws EOFException, IOException {
		boolean isEmptyArray = false;
		try(final JsonParser jsonParser = JSON_FACTORY.createParser(in)) {
			final JsonToken rootToken = jsonParser.nextToken();
			JsonToken nextToken;
			if(JsonToken.START_ARRAY.equals(rootToken)) {
				do {
					nextToken = jsonParser.nextToken();
					switch(nextToken) {
						case START_OBJECT:
							if(isInsideObjectToken) {
								LOG.debug(Markers.ERR, "Looks like the json response is not plain");
							}
							isInsideObjectToken = true;
							break;
						case END_OBJECT:
							if(isInsideObjectToken) {
								if(lastId != null && lastSize > -1) {
									try {
										offset = Long.parseLong(lastId, DataObject.ID_RADIX);
										if(offset < 0) {
											LOG.warn(
												Markers.ERR,
												"Calculated from id ring offset is negative"
											);
										} else {
											listPageBuffer.add(
												itemConstructor.newInstance(
													lastId, offset, lastSize
												)
											);
											count ++;
											isEmptyArray = true;
										}
									} catch(
										final InstantiationException | IllegalAccessException |
											InvocationTargetException e
									) {
										LogUtil.exception(
											LOG, Level.WARN, e,
											"Failed to create data item descriptor"
										);
									} catch(final NumberFormatException e) {
										LOG.debug(Markers.ERR, "Invalid id: {}", lastId);
									}
								} else {
									LOG.trace(
										Markers.ERR, "Invalid object id ({}) or size ({})",
										lastId, lastSize
									);
								}
							} else {
								LOG.debug(Markers.ERR, "End of json object is not inside object");
							}
							isInsideObjectToken = false;
							break;
						case FIELD_NAME:
							if(KEY_SIZE.equals(jsonParser.getCurrentName())) {
								lastSize = jsonParser.nextLongValue(-1);
							}
							if(KEY_ID.equals(jsonParser.getCurrentName())) {
								lastId = jsonParser.nextTextValue();
							}
							break;
						case VALUE_NUMBER_INT:
						case VALUE_STRING:
						case VALUE_NULL:
						case VALUE_FALSE:
						case VALUE_NUMBER_FLOAT:
						case VALUE_TRUE:
						case VALUE_EMBEDDED_OBJECT:
						case NOT_AVAILABLE:
						default:
							break;
					}
				} while(!JsonToken.END_ARRAY.equals(nextToken));
				// if container's list is empty
				if(isEmptyArray) {
					throw new EOFException();
				}
			} else {
				LOG.warn(
					Markers.ERR,
					"Response contains root JSON token \"{}\", but array token was expected"
				);
			}
		}
	}
}