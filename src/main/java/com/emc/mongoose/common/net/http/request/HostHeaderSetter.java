package com.emc.mongoose.common.net.http.request;
//
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
//
//import org.apache.log.log4j.LogManager;
//import org.apache.log.log4j.Logger;
/**
 Created by kurila on 30.01.15.
 */
public final class HostHeaderSetter
implements HttpRequestInterceptor {
	//
	//private final static Logger LOG = LogManager.getLogger();
	private final static String METHOD_CONNECT = "connect";
	//
	@Override
	public final void process(final HttpRequest req, final HttpContext ctx)
	throws ProtocolException {
		//
		final RequestLine reqLine = req.getRequestLine();
		if(
			METHOD_CONNECT.equalsIgnoreCase(reqLine.getMethod()) &&
			HttpVersion.HTTP_1_1.greaterEquals(reqLine.getProtocolVersion())
		) {
			return;
		}
		//
		if(!req.containsHeader(HTTP.TARGET_HOST)) {
			final HttpHost tgtHost;
			if(HttpCoreContext.class.isInstance(ctx)) {
				tgtHost = ((HttpCoreContext) ctx).getTargetHost();
				if(tgtHost == null) {
					throw new ProtocolException(
						"No target host is in the HTTP context #" + ctx.hashCode()
					);
				} else {
					req.setHeader(HTTP.TARGET_HOST, tgtHost.toHostString());
				}
			} else {
				throw new ProtocolException("Unable to obtain the target host for the request");
			}
		}
	}
}
