package com.emc.mongoose.storage.mock.impl.request;
//
import com.emc.mongoose.common.conf.RunTimeConfig;
//import com.emc.mongoose.common.logging.LogUtil;
//
import com.emc.mongoose.storage.mock.api.Storage;
import com.emc.mongoose.storage.mock.api.data.WSObjectMock;
//
import org.apache.http.HttpRequest;
//
import org.apache.http.nio.protocol.HttpAsyncRequestHandler;
import org.apache.http.nio.protocol.HttpAsyncRequestHandlerMapper;
//
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
/**
 Created by andrey on 13.05.15.
 */
public final class APIRequestHandlerMapper<T extends WSObjectMock>
implements HttpAsyncRequestHandlerMapper {
	//
	//private final static Logger LOG = LogManager.getLogger();
	//
	private final AtmosRequestHandler<T> reqHandlerAtmos;
	private final S3RequestHandler<T> reqHandlerS3;
	private final SwiftRequestHandler<T> reqHandlerSwift;
	//
	public APIRequestHandlerMapper(
		final RunTimeConfig runTimeConfig, final Storage<T> sharedStorage
	) {
		reqHandlerAtmos = new AtmosRequestHandler<>(runTimeConfig, sharedStorage);
		reqHandlerS3 =  new S3RequestHandler<>(runTimeConfig, sharedStorage);
		reqHandlerSwift = new SwiftRequestHandler<>(runTimeConfig, sharedStorage);
	}
	//
	@Override
	public final HttpAsyncRequestHandler<HttpRequest> lookup(final HttpRequest httpRequest) {
		final String requestURI = httpRequest.getRequestLine().getUri();
		if(reqHandlerAtmos.matches(requestURI)) {
			return reqHandlerAtmos;
		} else if(reqHandlerSwift.matches(requestURI)) {
			return reqHandlerSwift;
		} else {
			return reqHandlerS3;
		}
	}
}