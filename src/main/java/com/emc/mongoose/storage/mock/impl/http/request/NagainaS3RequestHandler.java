package com.emc.mongoose.storage.mock.impl.http.request;

import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
import com.emc.mongoose.core.api.item.data.ContainerHelper;
//
import com.emc.mongoose.storage.mock.api.ContainerMockException;
import com.emc.mongoose.storage.mock.api.ContainerMockNotFoundException;
import com.emc.mongoose.storage.mock.api.HttpDataItemMock;
import com.emc.mongoose.storage.mock.api.HttpStorageMock;
//
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import org.w3c.dom.Document;
import org.w3c.dom.Element;
//
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
//
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
//
import static io.netty.channel.ChannelHandler.Sharable;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
//
@Sharable
public final class NagainaS3RequestHandler<T extends HttpDataItemMock> extends NagainaRequestHandlerBase<T> {

	private static final Logger LOG = LogManager.getLogger();
	private static final DocumentBuilder DOM_BUILDER;
	private static final TransformerFactory TF = TransformerFactory.newInstance();

	private final int prefixLength, idRadix;

	static {
		try {
			DOM_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public NagainaS3RequestHandler(final AppConfig appConfig, HttpStorageMock<T> sharedStorage) {
		super(appConfig, sharedStorage);
		idRadix = appConfig.getItemNamingRadix();
		final String prefix = appConfig.getItemNamingPrefix();
		prefixLength = prefix == null ? 0 : prefix.length();
	}

	@Override
	protected final boolean checkApiMatch(final HttpRequest request) {
		return true;
	}

	@Override
	public final void handleActually(final ChannelHandlerContext ctx) {
		final String method = ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey))
				.get().getMethod().toString().toUpperCase();
		final String uri = ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey))
				.get().getUri();
		final String[] uriParams =
				getUriParams(uri, 2);
		final String containerName = uriParams[0];
		final String objId = uriParams[1];
		final Long size = ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)).get();
		ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).set(true);
		try {
			if (containerName != null) {
				if (objId != null) {
					final long offset;
					switch (method) {
						case HttpRequestConfig.METHOD_POST:
						case HttpRequestConfig.METHOD_PUT:
							if (prefixLength > 0) {
								offset = Long.parseLong(objId.substring(prefixLength + 1), idRadix);
							} else {
								offset = Long.parseLong(objId, idRadix);
							}
							break;
						default:
							offset = -1;
					}
					handleGenericDataReq(method, containerName, objId, offset, size, ctx);
				} else {
					handleGenericContainerReq(method, containerName, ctx);
				}
			} else {
				setHttpResponseStatusInContext(ctx, BAD_REQUEST);
			}
		} catch (final IllegalArgumentException | IllegalStateException e) {
			LogUtil.exception(
					LOG, Level.WARN, e, "Failed to parse the request URI: {}", uri
			);
			setHttpResponseStatusInContext(ctx, BAD_REQUEST);
		}
		writeResponse(ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).get(), ctx);
	}

	@Override
	protected final void handleContainerList(final String containerName, final ChannelHandlerContext ctx) {
		int maxCount = ContainerHelper.DEFAULT_PAGE_SIZE;
		String marker = null;
		final String uri = ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey)).get().getUri();
		QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
		if (queryStringDecoder.parameters().containsKey("max-keys")) {
			maxCount = Integer.parseInt(queryStringDecoder.parameters().get("max-keys").get(0));
		} else {
			LOG.warn(Markers.ERR, "Failed to parse max keys argument value in the URI: " + uri);
		}
		if (queryStringDecoder.parameters().containsKey("marker")) {
			marker = queryStringDecoder.parameters().get("marker").get(0);
		}
		final List<T> buff = new ArrayList<>(maxCount);
		T lastObj;
		try {
			lastObj = listContainer(containerName, marker, buff, maxCount);
		} catch (final ContainerMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			return;
		} catch (ContainerMockException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			return;
		}
		Document doc = DOM_BUILDER.newDocument();
		Element eRoot = doc.createElementNS(
				"http://s3.amazonaws.com/doc/2006-03-01/", "ListBucketResult"
		);
		doc.appendChild(eRoot);
		//
		Element element = doc.createElement("Name"), ee;
		element.appendChild(doc.createTextNode(containerName));
		eRoot.appendChild(element);
		element = doc.createElement("IsTruncated");
		element.appendChild(doc.createTextNode(Boolean.toString(lastObj != null)));
		eRoot.appendChild(element);
		element = doc.createElement("Prefix"); // TODO prefix support
		eRoot.appendChild(element);
		element = doc.createElement("MaxKeys");
		element.appendChild(doc.createTextNode(Integer.toString(buff.size())));
		eRoot.appendChild(element);
		for (final T dataObject : buff) {
			element = doc.createElement("Contents");
			ee = doc.createElement("Key");
			ee.appendChild(doc.createTextNode(dataObject.getName()));
			element.appendChild(ee);
			ee = doc.createElement("Size");
			ee.appendChild(doc.createTextNode(Long.toString(dataObject.getSize())));
			element.appendChild(ee);
			eRoot.appendChild(element);
		}
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		final StreamResult r = new StreamResult(bos);
		try {
			TF.newTransformer().transform(new DOMSource(doc), r);
		} catch (final TransformerException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to build bucket XML listing");
			return;
		}
		byte[] content = bos.toByteArray();
		ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).set(false);
		final FullHttpResponse
			response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(content));
		response.headers().set(CONTENT_TYPE, "application/xml");
		HttpHeaders.setContentLength(response, content.length);
		ctx.write(response);
	}

}
