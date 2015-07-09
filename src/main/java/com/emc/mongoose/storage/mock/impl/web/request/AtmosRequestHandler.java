package com.emc.mongoose.storage.mock.impl.web.request;
// mongoose-common.jar
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.storage.mock.api.ObjectStorage;
//
import com.emc.mongoose.storage.mock.impl.web.data.BasicWSObjectMock;
// mongoose-storage-adapter-atmos.jar
import com.emc.mongoose.storage.adapter.atmos.WSRequestConfigImpl;
import com.emc.mongoose.storage.adapter.atmos.WSSubTenantImpl;
//
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 Created by andrey on 13.05.15.
 */
public final class AtmosRequestHandler<T extends BasicWSObjectMock>
extends WSRequestHandlerBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static String URI_BASE_PATH = "/rest";
	//
	public AtmosRequestHandler(final RunTimeConfig runTimeConfig, final ObjectStorage<T> sharedStorage) {
		super(runTimeConfig, sharedStorage);
	}
	//
	public boolean matches(final String requestURI) {
		return requestURI != null && requestURI.startsWith(URI_BASE_PATH);
	}
	//
	@Override
	public final void handleActually(
		final HttpRequest httpRequest, final HttpResponse httpResponse, final String method,
		final String requestURI[], final String dataId
	) {
		if(requestURI.length > 2) {
			if(requestURI[2].equals(WSSubTenantImpl.SUBTENANT)) { // subtenant-related request
				if(method.equalsIgnoreCase(METHOD_PUT)) {
					final String subtenant = randomString(5);
					httpResponse.setHeader(WSSubTenantImpl.KEY_SUBTENANT_ID, subtenant);
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(Markers.MSG, "Created the subtenant: {}", subtenant);
					}
				}
				httpResponse.setStatusCode(HttpStatus.SC_OK);
			} else {
				if(requestURI[2].equals(WSRequestConfigImpl.API_TYPE_OBJ)) {
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG,
							"Handle atmos object request. URI doesn't contain the object ID."
						);
					}
					if(method.equals(METHOD_POST)) {
						final String
							newDataId = generateId(),
							headerLocation = httpRequest.getRequestLine().getUri()+"/"+newDataId;
						httpResponse.setHeader(HttpHeaders.LOCATION, headerLocation);
						handleGenericDataReq(httpRequest, httpResponse, method, newDataId);
					}
				} else {
					if(LOG.isTraceEnabled(Markers.MSG)) {
						LOG.trace(
							Markers.MSG, "Handle atmos request. URI contains the object ID."
						);
					}
					handleGenericDataReq(httpRequest, httpResponse, method, dataId);
				}
			}
		} else {
			httpResponse.setStatusCode(HttpStatus.SC_BAD_REQUEST);
		}
	}
}