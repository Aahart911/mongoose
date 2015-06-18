package com.emc.mongoose.storage.mock.impl.response;
//
//import com.emc.mongoose.common.collections.InstancePool;
//import com.emc.mongoose.common.collections.Reusable;
import com.emc.mongoose.common.io.HTTPContentEncoderChannel;
import com.emc.mongoose.common.logging.LogUtil;
//
import com.emc.mongoose.storage.mock.api.data.WSObjectMock;
//
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
//
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncResponseProducer;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
/**
 * Created by olga on 12.02.15.
 */
public final class BasicWSResponseProducer
implements HttpAsyncResponseProducer {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private volatile HttpResponse response = null;
	private final HTTPContentEncoderChannel chanOut = new HTTPContentEncoderChannel();
	//
	public final void setResponse(final HttpResponse response) {
		this.response = response;
	}
	//
	@Override
	public HttpResponse generateResponse() {
		return this.response;
	}
	//
	@Override
	public final void produceContent(final ContentEncoder encoder, final IOControl ioctrl)
	throws IOException {
		chanOut.setContentEncoder(encoder);
		try {
			final WSObjectMock dataItem = WSObjectMock.class.cast(response.getEntity());
			if(dataItem != null) {
				dataItem.write(chanOut);
			}
		} catch(final Exception e) {
			LogUtil.exception(LOG, Level.WARN, e, "Content producing failure");
		} finally {
			encoder.complete();
			chanOut.close();
		}
	}
	//
	@Override
	public final void responseCompleted(final HttpContext context) {
	}
	//
	@Override
	public final void failed(final Exception e) {
		LogUtil.exception(LOG, Level.WARN, e, "Response failure");
	}
	//
	@Override
	public final void close()
	throws IOException {
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Reusable implementation /////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	/*private final static InstancePool<BasicWSResponseProducer> POOL;
	static {
		InstancePool<BasicWSResponseProducer> t = null;
		try {
			t = new InstancePool<>(BasicWSResponseProducer.class.getConstructor());
		} catch(final NoSuchMethodException e) {
			LogUtil.exception(LOG, Level.FATAL, e, "Failed to create the instance pool");
		}
		POOL = t;
	}
	//
	public static BasicWSResponseProducer getInstance(final HttpResponse response) {
		return POOL.take(response);
	}
	//
	@Override
	public final Reusable<BasicWSResponseProducer> reuse(final Object... args)
	throws IllegalArgumentException, IllegalStateException {
		if(args != null) {
			if(args.length > 0) {
				response = HttpResponse.class.cast(args[0]);
			}
		}
		return this;
	}
	//
	@Override
	public final void release() {
		POOL.release(this);
	}*/
}