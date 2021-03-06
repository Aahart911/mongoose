package com.emc.mongoose.storage.adapter.swift;
// mongoose-common.jar
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api.jar
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.item.data.ContainerHelper;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
//
import com.emc.mongoose.core.impl.item.data.HttpContainerHelperBase;
//
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 03.03.15.
 */
public final class HttpSwiftContainerHelper<T extends HttpDataItem, C extends Container<T>>
extends HttpContainerHelperBase<T, C>
implements SwiftContainerHelper<T, C> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public HttpSwiftContainerHelper(final HttpRequestConfigImpl<T, C> reqConf, final C container) {
		super(reqConf, container);
	}
	//
	@Override
	public final boolean exists(final String addr)
	throws IllegalStateException {
		boolean flagExists = false;
		//
		try {
			final HttpResponse httpResp = execute(
				addr, HttpRequestConfig.METHOD_HEAD, null, ContainerHelper.DEFAULT_PAGE_SIZE,
				HttpRequestConfig.REQUEST_NO_PAYLOAD_TIMEOUT_SEC, TimeUnit.SECONDS
			);
			if(httpResp != null) {
				final HttpEntity httpEntity = httpResp.getEntity();
				final StatusLine statusLine = httpResp.getStatusLine();
				if(statusLine == null) {
					LOG.warn(Markers.MSG, "No response status");
				} else {
					final int statusCode = statusLine.getStatusCode();
					if(statusCode >= 200 && statusCode < 300) {
						LOG.debug(Markers.MSG, "Container \"{}\" exists", containerName);
						flagExists = true;
					} else if(statusCode == HttpStatus.SC_NOT_FOUND) {
						LOG.debug(Markers.MSG, "Container \"{}\" doesn't exist", containerName);
					} else {
						final StringBuilder msg = new StringBuilder("Check container \"")
							.append(containerName).append("\" failure: ")
							.append(statusLine.getReasonPhrase());
						if(httpEntity != null) {
							try(final ByteArrayOutputStream buff = new ByteArrayOutputStream()) {
								httpEntity.writeTo(buff);
								msg.append('\n').append(buff.toString());
							}
						}
						throw new IllegalStateException(msg.toString());
					}
				}
				EntityUtils.consumeQuietly(httpEntity);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "HTTP request execution failure");
		}
		//
		return flagExists;
	}
	//
	@Override
	public final void create(final String addr)
	throws IllegalStateException {
		try {
			final HttpResponse httpResp = execute(
				addr, HttpRequestConfig.METHOD_PUT, null, ContainerHelper.DEFAULT_PAGE_SIZE,
				HttpRequestConfig.REQUEST_NO_PAYLOAD_TIMEOUT_SEC, TimeUnit.SECONDS
			);
			if(httpResp != null) {
				final HttpEntity httpEntity = httpResp.getEntity();
				final StatusLine statusLine = httpResp.getStatusLine();
				if(statusLine == null) {
					LOG.warn(Markers.MSG, "No response status");
				} else {
					final int statusCode = statusLine.getStatusCode();
					if(statusCode >= 200 && statusCode < 300) {
						LOG.info(Markers.MSG, "Container \"{}\" created", containerName);
					} else {
						final StringBuilder msg = new StringBuilder("Create container \"")
							.append(containerName).append("\" failure: ")
							.append(statusLine.getReasonPhrase());
						if(httpEntity != null) {
							try(final ByteArrayOutputStream buff = new ByteArrayOutputStream()) {
								httpEntity.writeTo(buff);
								msg.append('\n').append(buff.toString());
							}
						}
						LOG.warn(
							Markers.ERR, "Create container \"{}\" response ({}): {}",
							containerName, statusCode, msg.toString()
						);
					}
				}
				EntityUtils.consumeQuietly(httpEntity);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "HTTP request execution failure");
		}
	}
	//
	@Override
	public final void delete(final String addr)
	throws IllegalStateException {
		//
		try {
			final HttpResponse httpResp = execute(
				addr, HttpRequestConfig.METHOD_DELETE, null, ContainerHelper.DEFAULT_PAGE_SIZE,
				HttpRequestConfig.REQUEST_NO_PAYLOAD_TIMEOUT_SEC, TimeUnit.SECONDS
			);
			if(httpResp != null) {
				final HttpEntity httpEntity = httpResp.getEntity();
				final StatusLine statusLine = httpResp.getStatusLine();
				if(statusLine == null) {
					LOG.warn(Markers.MSG, "No response status");
				} else {
					final int statusCode = statusLine.getStatusCode();
					if(statusCode >= 200 && statusCode < 300) {
						LOG.info(Markers.MSG, "Container \"{}\" deleted", containerName);
					} else {
						final StringBuilder msg = new StringBuilder("Delete container \"")
							.append(containerName).append("\" failure: ")
							.append(statusLine.getReasonPhrase());
						if(httpEntity != null) {
							try(final ByteArrayOutputStream buff = new ByteArrayOutputStream()) {
								httpEntity.writeTo(buff);
								msg.append('\n').append(buff.toString());
							}
						}
						LOG.warn(
							Markers.ERR, "Delete container \"{}\" response ({}): {}",
							containerName, statusCode, msg.toString()
						);
					}
				}
				EntityUtils.consumeQuietly(httpEntity);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "HTTP request execution failure");
		}
	}
	//
	@Override
	public final void setVersioning(final String addr, final boolean enabledFlag) {
		try {
			final HttpResponse httpResp = execute(
				addr, HttpRequestConfig.METHOD_POST, null, ContainerHelper.DEFAULT_PAGE_SIZE,
				HttpRequestConfig.REQUEST_NO_PAYLOAD_TIMEOUT_SEC, TimeUnit.SECONDS
			);
			if(httpResp != null) {
				final HttpEntity httpEntity = httpResp.getEntity();
				final StatusLine statusLine = httpResp.getStatusLine();
				if(statusLine == null) {
					LOG.warn(Markers.MSG, "No response status");
				} else {
					final int statusCode = statusLine.getStatusCode();
					if(statusCode >= 200 && statusCode < 300) {
						LOG.info(Markers.MSG, "Container \"{}\" created", containerName);
					} else {
						final StringBuilder msg = new StringBuilder("Create container \"")
							.append(containerName).append("\" failure: ")
							.append(statusLine.getReasonPhrase());
						if(httpEntity != null) {
							try(final ByteArrayOutputStream buff = new ByteArrayOutputStream()) {
								httpEntity.writeTo(buff);
								msg.append('\n').append(buff.toString());
							}
						}
						LOG.warn(
							Markers.ERR, "Create container \"{}\" response ({}): {}",
							containerName, statusCode, msg.toString()
						);
					}
				}
				EntityUtils.consumeQuietly(httpEntity);
			}
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "HTTP request execution failure");
		}
	}
	//
	private final static String MSG_INVALID_METHOD = "<NULL> is invalid HTTP method";
	//
	final HttpResponse execute(
		final String addr, final String method, final String nextMarker, final long maxCount,
		final long timeOut, final TimeUnit timeUnit
	) throws IOException {
		//
		if(method == null) {
			throw new IllegalArgumentException(MSG_INVALID_METHOD);
		}
		//
		final HttpEntityEnclosingRequest httpReq;
		//
		switch(method) {
			case HttpRequestConfig.METHOD_GET:
				if(nextMarker == null) {
					httpReq = reqConf.createGenericRequest(
						method,
						"/" + HttpRequestConfigImpl.class.cast(reqConf).getSvcBasePath() + "/" +
						reqConf.getNameSpace() + "/" + containerName + "?format=json&limit=" +
						maxCount
					);
				} else {
					httpReq = reqConf.createGenericRequest(
						method,
						"/" + HttpRequestConfigImpl.class.cast(reqConf).getSvcBasePath() + "/" +
						reqConf.getNameSpace() + "/" + containerName + "?format=json&limit=" +
						maxCount + "&marker=" + nextMarker
					);
				}
				break;
			case HttpRequestConfig.METHOD_PUT:
				httpReq = reqConf.createGenericRequest(
					method,
					"/" + HttpRequestConfigImpl.class.cast(reqConf).getSvcBasePath() + "/" +
						reqConf.getNameSpace() + "/" + containerName
				);
				httpReq.setHeader(
					new BasicHeader(
						HttpRequestConfig.KEY_EMC_FS_ACCESS,
						Boolean.toString(reqConf.getFileAccessEnabled())
					)
				);
				break;
			case HttpRequestConfig.METHOD_POST:
				httpReq = reqConf.createGenericRequest(
					method,
					"/" + HttpRequestConfigImpl.class.cast(reqConf).getSvcBasePath() + "/" +
						reqConf.getNameSpace() + "/" + containerName
				);
				if(reqConf.getVersioning()) {
					httpReq.setHeader(
						new BasicHeader(
							HttpRequestConfigImpl.KEY_X_VERSIONING,
							HttpRequestConfigImpl.DEFAULT_VERSIONS_CONTAINER
						)
					);
				} else {
					httpReq.setHeader(new BasicHeader(HttpRequestConfigImpl.KEY_X_VERSIONING, ""));
				}
				break;
			default:
				httpReq = reqConf.createGenericRequest(
					method,
					"/" + HttpRequestConfigImpl.class.cast(reqConf).getSvcBasePath() + "/" +
						reqConf.getNameSpace() + "/" + containerName
				);
		}
		//
		return reqConf.execute(addr, httpReq, timeOut, timeUnit);
	}
}
