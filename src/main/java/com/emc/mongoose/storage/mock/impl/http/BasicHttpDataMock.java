package com.emc.mongoose.storage.mock.impl.http;
//
import com.emc.mongoose.core.api.item.data.ContentSource;
import com.emc.mongoose.storage.mock.api.HttpDataItemMock;
import com.emc.mongoose.storage.mock.impl.base.BasicMutableDataItemMock;
//
import org.apache.http.Header;
import org.apache.http.util.EntityUtils;
//
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
/**
 Created by kurila on 27.07.15.
 */
public final class BasicHttpDataMock
extends BasicMutableDataItemMock
implements HttpDataItemMock {
	//
	public BasicHttpDataMock() {
		super();
	}
	//
	public BasicHttpDataMock(final String value, final ContentSource contentSrc) {
		super(value, contentSrc);
	}
	//
	public BasicHttpDataMock(final long offset, final long size, final ContentSource contentSrc) {
		super(offset, size, contentSrc);
	}
	//
	public BasicHttpDataMock(
		final String name, final long offset, final long size, final ContentSource contentSrc
	) {
		super(name, offset, size, 0, contentSrc);
	}
	//
	public BasicHttpDataMock(
		final String name, final long offset, final long size, final int layerNum,
		final ContentSource contentSrc
	) {
		super(name, offset, size, layerNum, contentSrc);
	}
	//
	@Override
	public final boolean isRepeatable() {
		return IS_CONTENT_REPEATABLE;
	}
	//
	@Override
	public final boolean isChunked() {
		return IS_CONTENT_CHUNKED;
	}
	//
	@Override
	public final long getContentLength() {
		return size;
	}
	//
	@Override
	public final Header getContentType() {
		return HEADER_CONTENT_TYPE;
	}
	//
	@Override
	public final Header getContentEncoding() {
		return null;
	}
	//
	@Override
	public final InputStream getContent()
	throws IOException, UnsupportedOperationException {
		throw new UnsupportedOperationException("Shouldn't be invoked");
	}
	//
	@Override
	public final void writeTo(final OutputStream outstream)
	throws IOException {
		throw new UnsupportedOperationException("Shouldn't be invoked");
	}
	//
	@Override
	public final boolean isStreaming() {
		return true;
	}
	//
	@Override @Deprecated
	public final void consumeContent()
	throws IOException {
		EntityUtils.consume(this);
	}
}
