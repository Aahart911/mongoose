package com.emc.mongoose.object.api.impl.provider.s3;
//
import com.emc.mongoose.base.load.Consumer;
import com.emc.mongoose.object.data.DataObject;
import com.emc.mongoose.util.logging.TraceLogger;
import com.emc.mongoose.object.data.WSObject;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
//
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.rmi.RemoteException;
import java.util.concurrent.RejectedExecutionException;
/**
 Created by kurila on 09.10.14.
 */
public final class XMLBucketListParser<T extends WSObject>
extends DefaultHandler {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final static String
		QNAME_ITEM = "Contents",
		QNAME_ITEM_ID = "Key",
		QNAME_ITEM_SIZE = "Size";
	//
	private final Consumer<T> consumer;
	private final Constructor<T> dataConstructor;
	private final long maxCount;
	private volatile long count = 0;
	private volatile boolean
		isInsideItem = false,
		isInsideItemId = false,
		isInsideItemSize = false;
	private volatile String
		strId = null, strSize = null;
	//
	XMLBucketListParser(
		final Consumer<T> consumer, final Constructor<T> dataConstructor, final long maxCount
	) {
		this.consumer = consumer;
		this.dataConstructor = dataConstructor;
		this.maxCount = maxCount;
	}
	//
	@Override
	public final void startElement(
		final String uri, final String localName, final String qName, Attributes attrs
	) throws SAXException {
		isInsideItem = isInsideItem || QNAME_ITEM.equals(qName);
		isInsideItemId = isInsideItem && QNAME_ITEM_ID.equals(qName);
		isInsideItemSize = isInsideItem && QNAME_ITEM_SIZE.equals(qName);
		super.startElement(uri, localName, qName, attrs);
	}
	//
	private final ByteBuffer offsetValueBytes = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
	private final LongBuffer offsetValueView = offsetValueBytes.asLongBuffer();
	//
	@Override @SuppressWarnings("unchecked")
	public final void endElement(
		final String uri, final String localName, final String qName
	) throws SAXException {
		//
		isInsideItemId = isInsideItemId && !QNAME_ITEM_ID.equals(qName);
		isInsideItemSize = isInsideItemSize && !QNAME_ITEM_SIZE.equals(qName);
		//
		if(isInsideItem && QNAME_ITEM.equals(qName)) {
			isInsideItem = false;
			//
			long offset, size = 0;
			//
			if(strSize != null && strSize.length() > 0) {
				try {
					size = Long.parseLong(strSize);
				} catch(final NumberFormatException e) {
					TraceLogger.failure(
						LOG, Level.WARN, e, "Data object size should be a 64 bit number"
					);
				}
			} else {
				LOG.trace(Markers.ERR, "No \"{}\" element or empty", QNAME_ITEM_SIZE);
			}
			//
			if(strId != null && strId.length() > 0 && size > 0) {
				try {
					offset = Long.parseLong(strId, DataObject.ID_RADIX);
					if(offset < 0) {
						LOG.warn(Markers.ERR, "Calculated from id ring offset is negative");
					} else if(count < maxCount) {
						consumer.submit(dataConstructor.newInstance(strId, offset, size));
						count ++;
					} else {
						endDocument();
					}
				} catch(final RemoteException | RejectedExecutionException e) {
					TraceLogger.failure(
						LOG, Level.WARN, e, "Failed to submit new data object to the consumer"
					);
				} catch(final InterruptedException e) {
					endDocument();
				} catch(final NumberFormatException e) {
					LOG.debug(Markers.ERR, "Invalid id: {}", strId);
				} catch(final Exception e) {
					TraceLogger.failure(LOG, Level.ERROR, e, "Unexpected failure");
				}
			} else {
				LOG.trace(Markers.ERR, "Invalid object id ({}) or size ({})", strId, strSize);
			}
		}
		//
		super.endElement(uri, localName, qName);
	}
	//
	@Override
	public final void characters(
		final char buff[], final int start, final int length
	) throws SAXException {
		if(isInsideItemId) {
			strId = new String(buff, start, length);
		} else if(isInsideItemSize) {
			strSize = new String(buff, start, length);
		}
		super.characters(buff, start, length);
	}
	//
	@Override
	public final void endDocument()
	throws SAXException {
		LOG.debug(Markers.MSG, "End of bucket listing, got {} items", count);
		if(consumer != null) {
			try {
				consumer.shutdown();
			} catch(final RemoteException e) {
				TraceLogger.failure(
					LOG, Level.WARN, e, "Failed to limit data items count for remote consumer"
				);
			}
		}
		super.endDocument();
	}
}