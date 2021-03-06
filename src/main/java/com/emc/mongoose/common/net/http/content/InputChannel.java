package com.emc.mongoose.common.net.http.content;
//
import static com.emc.mongoose.common.conf.Constants.BUFF_SIZE_LO;
import com.emc.mongoose.common.net.http.ContentUtil;
import org.apache.http.nio.ContentDecoder;
//
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
/**
 Created by kurila on 20.05.15.
 */
public final class InputChannel
implements ReadableByteChannel {
	//
	private final ContentDecoder contentDecoder;
	//
	public InputChannel(final ContentDecoder contentDecoder) {
		this.contentDecoder = contentDecoder;
	}
	//
	@Override
	public final int read(final ByteBuffer src)
	throws IOException {
		return contentDecoder.read(src);
	}
	//
	@Override
	public final void close() {
		long doneByteCount = 0;
		while(isOpen()) {
			doneByteCount += ContentUtil.consumeQuietly(
				contentDecoder, doneByteCount > 0 ? doneByteCount : BUFF_SIZE_LO
			);
		}
	}
	//
	@Override
	public final boolean isOpen() {
		return !contentDecoder.isCompleted();
	}
}
