package com.emc.mongoose.storage.mock.impl.http.request;

import com.emc.mongoose.model.api.data.ContentSource;
import com.emc.mongoose.storage.mock.api.MutableDataItemMock;
import com.emc.mongoose.storage.mock.api.StorageMock;
import com.emc.mongoose.ui.config.Config;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.emc.mongoose.storage.mock.impl.http.request.XmlShortcuts.appendElement;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 Created on 12.07.16.
 */
@Sharable
public class S3RequestHandler<T extends MutableDataItemMock>
	extends RequestHandlerBase<T> {


	private static final Logger LOG = LogManager.getLogger();
	private static final DocumentBuilder DOM_BUILDER;
	private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();
	private static final String S3_NAMESPACE_URI = "http://s3.amazonaws.com/doc/2006-03-01/";

	static {
		try {
			DOM_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public S3RequestHandler(
			final Config.ItemConfig.NamingConfig namingConfig,
			final Config.LoadConfig.LimitConfig limitConfig,
			final StorageMock<T> sharedStorage,
			final ContentSource contentSource) {
		super(limitConfig, sharedStorage, contentSource);
		final String prefix = namingConfig.getPrefix();
		if (prefix != null) {
			setPrefixLength(prefix.length());
		} else {
			setPrefixLength(0);
		}
		setIdRadix(namingConfig.getRadix());
	}

	@Override
	protected void doHandle(
			final String uri,
			final HttpMethod method,
			final Long size,
			final ChannelHandlerContext ctx) {
		final String[] uriParams = getUriParameters(uri, 2);
		final String containerName = uriParams[0];
		final String objectId = uriParams[1];
		final Channel channel = ctx.channel();
		channel.attr(AttributeKey.<Boolean>valueOf(CTX_WRITE_FLAG_KEY)).set(true);
		if (containerName != null) {
			handleItemRequest(uri, method, containerName, objectId, size, ctx);
		} else {
			setHttpResponseStatusInContext(ctx, BAD_REQUEST);
		}
		if (channel.attr(AttributeKey.<Boolean>valueOf(CTX_WRITE_FLAG_KEY)).get()) {
			writeResponse(ctx);
		}
	}

	private static final String MAX_COUNT_KEY = "max-keys";

	@Override
	protected void handleContainerList(
			final String name,
			final QueryStringDecoder queryStringDecoder,
			final ChannelHandlerContext ctx) {
		int maxCount = DEFAULT_PAGE_SIZE;
		String marker = null;
		final Map<String, List<String>> parameters = queryStringDecoder.parameters();
		if (parameters.containsKey(MAX_COUNT_KEY)) {
			maxCount = Integer.parseInt(parameters.get(MAX_COUNT_KEY).get(0));
		} else {
			LOG.warn(Markers.ERR, "Failed to parse max keys argument value in the URI {}",
					queryStringDecoder.uri());
		}
		if (parameters.containsKey(MARKER_KEY)) {
			marker = parameters.get(MARKER_KEY).get(0);
		}
		final List<T> buffer = new ArrayList<>(maxCount);
		final T lastObj = listContainer(name, marker, buffer, maxCount);
		final Document xml = DOM_BUILDER.newDocument();
		final Element rootElem =
				xml.createElementNS(S3_NAMESPACE_URI, "ListBucketResult");
		xml.appendChild(rootElem);
		appendElement(xml, rootElem, "Name", name);
		appendElement(xml, rootElem, "IsTruncated", Boolean.toString(lastObj != null));
		appendElement(xml, rootElem, "Prefix");
		appendElement(xml, rootElem, "MaxKeys", Integer.toString(buffer.size()));
		for (final T object: buffer) {
			final Element elem = xml.createElement("Contents");
			appendElement(xml, elem, "Key", object.getName());
			appendElement(xml, elem, "Size", Long.toString(object.getSize()));
			appendElement(rootElem, elem);
		}
		final ByteArrayOutputStream stream = new ByteArrayOutputStream();
		final StreamResult streamResult = new StreamResult(stream);
		try {
			TRANSFORMER_FACTORY.newTransformer().transform(new DOMSource(xml), streamResult);
		} catch (final TransformerException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to build bucket XML listing");
			return;
		}
		final byte[] content = stream.toByteArray();
		ctx.channel().attr(AttributeKey.<Boolean>valueOf(CTX_WRITE_FLAG_KEY)).set(false);
		final FullHttpResponse
				response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(content));
		response.headers().set(CONTENT_TYPE, "application/xml");
		HttpUtil.setContentLength(response, content.length);
		ctx.write(response);
	}


}
