package com.emc.mongoose.web.api.impl.provider.s3;
//
import com.emc.mongoose.web.api.WSRequestConfig;
import com.emc.mongoose.web.data.WSObject;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
//
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
/**
 Created by kurila on 02.10.14.
 */
public class Bucket<T extends WSObject>
implements com.emc.mongoose.object.api.provider.s3.Bucket<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static String VERSIONING_ENTITY_CONTENT =
		"<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">\n" +
		"	<Status>Enabled</Status>\n" +
		"	<MfaDelete>Disabled</MfaDelete>\n" +
		"</VersioningConfiguration>";
	//
	final RequestConfig reqConf;
	final String name;
	//
	public Bucket(final RequestConfig reqConf, final String name) {
		this.reqConf = reqConf;
		//
		if(name == null || name.length() == 0) {
			final Date
				dt = Calendar.getInstance(
					TimeZone.getTimeZone("GMT+0"), Locale.ROOT
				).getTime();
			this.name = "mongoose-" + WSRequestConfig.FMT_DT.format(dt);
		} else {
			this.name = name;
		}
	}
	//
	@Override
	public final String getName() {
		return name;
	}
	//
	private final static String FMT_MSG_INVALID_METHOD = "Invalid HTTP method name: \"%s\"";
	//
	final CloseableHttpResponse execute(final String method)
	throws IOException {
		//
		if(method == null || method.length() < 3) {
			throw new IllegalArgumentException(String.format(FMT_MSG_INVALID_METHOD, method));
		}
		//
		final RequestBuilder reqBuilder = RequestBuilder.create(method.toUpperCase());
		reqBuilder.setUri("/" + name);
		//
		if("put".equals(method)) {
			reqBuilder.setHeader(
				new BasicHeader(
					RequestConfig.KEY_EMC_BUCKET_FS,
					Boolean.toString(reqConf.getBucketFileSystem())
				)
			);
			if(reqConf.getBucketVersioning()) {
				reqBuilder.setEntity(
					new StringEntity(VERSIONING_ENTITY_CONTENT, ContentType.APPLICATION_XML)
				);
			}
		}
		//
		final HttpUriRequest httpReq = reqBuilder.build();
		//
		reqConf.applyHeadersFinally(httpReq);
		final CloseableHttpClient httpClient = reqConf.getClient();
		//
		if(httpClient == null) {
			throw new IllegalStateException("No HTTP client specified");
		}
		return httpClient.execute(
			new HttpHost(reqConf.getAddr(), reqConf.getPort(), reqConf.getScheme()),
			httpReq
		);
	}
	//
	@Override
	public final boolean exists()
	throws IllegalStateException {
		boolean flagExists = false;
		//
		try(final CloseableHttpResponse httpResp = execute("head")) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					LOG.debug(Markers.MSG, "Bucket \"{}\" exists", name);
					flagExists = true;
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.debug(
						Markers.MSG, "Checking bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
			EntityUtils.consumeQuietly(httpResp.getEntity());
		} catch(final IOException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to check the bucket \""+name+"\"");
		}
		//
		return flagExists;
	}
	//
	@Override
	public final void create()
	throws IllegalStateException {
		//
		try(final CloseableHttpResponse httpResp = execute("put")) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					LOG.info(Markers.MSG, "Bucket \"{}\" created", name);
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.warn(
						Markers.ERR, "Create bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
			EntityUtils.consumeQuietly(httpResp.getEntity());
		} catch(final IOException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to create the bucket \""+name+"\"");
		}
	}
	//
	@Override
	public final void delete()
	throws IllegalStateException {
		//
		try(final CloseableHttpResponse httpResp = execute("delete")) {
			final StatusLine statusLine = httpResp.getStatusLine();
			if(statusLine == null) {
				LOG.warn(Markers.MSG, "No response status");
			} else {
				final int statusCode = statusLine.getStatusCode();
				if(statusCode == HttpStatus.SC_OK) {
					LOG.info(Markers.MSG, "Bucket \"{}\" deleted", name);
				} else {
					final String statusMsg = statusLine.getReasonPhrase();
					LOG.warn(
						Markers.ERR, "Delete bucket \"{}\" response: {}/{}",
						name, statusCode, statusMsg
					);
				}
			}
			EntityUtils.consumeQuietly(httpResp.getEntity());
		} catch(final IOException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to delete the bucket \""+name+"\"");
		}
		//
	}
	//
}
