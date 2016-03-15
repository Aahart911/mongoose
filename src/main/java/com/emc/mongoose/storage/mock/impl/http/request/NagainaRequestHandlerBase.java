package com.emc.mongoose.storage.mock.impl.http.request;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
import com.emc.mongoose.storage.mock.api.HttpDataItemMock;
import com.emc.mongoose.storage.mock.api.HttpStorageMock;
import com.emc.mongoose.storage.mock.api.StorageIOStats;
import com.emc.mongoose.storage.mock.api.ContainerMockNotFoundException;
import com.emc.mongoose.storage.mock.api.ContainerMockException;
import com.emc.mongoose.storage.mock.api.ObjectMockNotFoundException;
import com.emc.mongoose.storage.mock.api.StorageMockCapacityLimitReachedException;
//
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
//
import static io.netty.channel.ChannelHandler.Sharable;
import static com.emc.mongoose.core.api.io.conf.HttpRequestConfig.VALUE_RANGE_CONCAT;
import static com.emc.mongoose.core.api.io.conf.HttpRequestConfig.VALUE_RANGE_PREFIX;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.RANGE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
//
@Sharable
public abstract class NagainaRequestHandlerBase<T extends HttpDataItemMock>
extends ChannelInboundHandlerAdapter {

	private final static Logger LOG = LogManager.getLogger();

	protected final int batchSize;
	private final float rateLimit;
	private final AtomicInteger lastMilliDelay = new AtomicInteger(1);

	protected final HttpStorageMock<T> sharedStorage;
	private final StorageIOStats ioStats;

	protected final String requestKey = "requestKey";
	protected final String responseStatusKey = "responseStatusKey";
	protected final String contentLengthKey = "contentLengthKey";
	protected final String ctxWriteFlagKey = "ctxWriteFlagKey";
	protected final String handlerStatus = "handlerStatus";

	public NagainaRequestHandlerBase(final AppConfig appConfig, final HttpStorageMock<T> sharedStorage) {
		this.rateLimit = (float) appConfig.getLoadLimitRate();
		this.batchSize = appConfig.getItemSrcBatchSize();
		this.sharedStorage = sharedStorage;
		this.ioStats = sharedStorage.getStats();
		AttributeKey.<HttpRequest>valueOf(requestKey);
		AttributeKey.<HttpResponseStatus>valueOf(responseStatusKey);
		AttributeKey.<Long>valueOf(contentLengthKey);
		AttributeKey.<Boolean>valueOf(ctxWriteFlagKey);
		AttributeKey.<Boolean>valueOf(handlerStatus);
	}

	abstract protected boolean checkApiMatch(final HttpRequest request);

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		if (!ctx.attr(AttributeKey.<Boolean>valueOf(handlerStatus)).get()) {
			ctx.fireChannelReadComplete();
			return;
		}
		ctx.flush();
	}

	private void processHttpRequest(final ChannelHandlerContext ctx, final HttpRequest request) {
		ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey)).set(request);
		if (request.headers().contains(CONTENT_LENGTH)) {
			ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)).set(Long.parseLong(request.headers().get(CONTENT_LENGTH)));
		}
	}

	private void processHttpContent(final ChannelHandlerContext ctx, final HttpContent httpContent) {
		final ByteBuf content = httpContent.content();
		if (ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)) == null) {
			Long currentContentSize = ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)).get();
			ctx.attr(AttributeKey.<Long>valueOf(contentLengthKey)).set(currentContentSize + content.readableBytes());
		}
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof HttpRequest) {
			// TODO the branch below fails if the request doesn't match the API
			if (!checkApiMatch((HttpRequest) msg)) {
				ctx.attr(AttributeKey.<Boolean>valueOf(handlerStatus)).set(false);
				ctx.fireChannelRead(msg);
				return;
			}
			ctx.attr(AttributeKey.<Boolean>valueOf(handlerStatus)).set(true);
			processHttpRequest(ctx, (HttpRequest) msg);
			ReferenceCountUtil.release(msg);
			return;
		}
		if (!ctx.attr(AttributeKey.<Boolean>valueOf(handlerStatus)).get()) {
			ctx.fireChannelRead(msg);
			return;
		}
//		Calculation of the content size if the request does not have such header (excessively)
//		if (msg instanceof HttpContent) {
//			processHttpContent(ctx, (HttpContent) msg);
//		}
		if (msg instanceof LastHttpContent) {
			handle(ctx);
		}
		ReferenceCountUtil.release(msg);
	}

	public final void handle(final ChannelHandlerContext ctx) {
		if (rateLimit > 0) {
			if (ioStats.getWriteRate() + ioStats.getReadRate() + ioStats.getDeleteRate() > rateLimit) {
				try {
					Thread.sleep(lastMilliDelay.incrementAndGet());
				} catch (InterruptedException e) {
					return;
				}
			} else if (lastMilliDelay.get() > 0) {
				lastMilliDelay.decrementAndGet();
			}
		}
		handleActually(ctx);
	}

	protected final String[] getUriParams(final String uri, final int maxNumberOfParams) {
		final String[] result = new String[maxNumberOfParams];
		final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
		final String[] pathChunks = queryStringDecoder.path().split("/");
		System.arraycopy(pathChunks, 1, result, 0, pathChunks.length - 1);
		return result;
	}

	protected abstract void handleActually(final ChannelHandlerContext ctx);

	protected final void writeResponse(final boolean writeFlag, final ChannelHandlerContext ctx) {
		if (writeFlag) {
			HttpResponseStatus status = ctx.attr(AttributeKey.<HttpResponseStatus>valueOf(responseStatusKey)).get();
			FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status != null ? status : OK);
			HttpHeaders.setContentLength(response, 0);
			ctx.write(response);
		}
	}

	protected final void handleGenericDataReq(
		final String method, final String containerName, final String objId,
		final Long offset, final Long size, final ChannelHandlerContext ctx
	) {
		if (containerName != null) {
			switch (method) {
				case HttpRequestConfig.METHOD_POST:
				case HttpRequestConfig.METHOD_PUT:
					handleWrite(containerName, objId, offset, size, ctx);
					break;
				case HttpRequestConfig.METHOD_GET:
					handleRead(containerName, objId, offset, ctx);
					break;
				case HttpRequestConfig.METHOD_HEAD:
					setHttpResponseStatusInContext(ctx, OK);
					break;
				case HttpRequestConfig.METHOD_DELETE:
					handleDelete(containerName, objId, offset, ctx);
					break;
			}
		} else {
			setHttpResponseStatusInContext(ctx, BAD_REQUEST);
		}
	}

	private void handleWrite(
		final String containerName, final String objId, final Long offset, final Long size,
		final ChannelHandlerContext ctx
	) {
		final List<String> rangeHeadersValues =
			ctx.attr(AttributeKey.<HttpRequest>valueOf(requestKey)).get().headers().getAll(RANGE);
		try {
			if (rangeHeadersValues.size() == 0) {
				sharedStorage.createObject(containerName, objId, offset, size);
				ioStats.markWrite(true, size);
			} else {
				ioStats.markWrite(
					handlePartialWrite(containerName, objId, rangeHeadersValues, size),
					size
				);
			}
		} catch (final StorageMockCapacityLimitReachedException e) {
			setHttpResponseStatusInContext(ctx, INSUFFICIENT_STORAGE);
			ioStats.markWrite(false, size);
		} catch (final ContainerMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			ioStats.markWrite(false, size);
		} catch (final ObjectMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			ioStats.markWrite(false, 0);
		} catch (final ContainerMockException | NumberFormatException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			ioStats.markWrite(false, 0);
			LogUtil.exception(
					LOG, Level.ERROR, e, "Failed to perform a range update/append for \"{}\"", objId
			);
		}
	}

	private boolean handlePartialWrite(final String containerName, final String objId,
	                                   final List<String> rangeHeadersValues, final Long size)
	throws ContainerMockException, ObjectMockNotFoundException {
		for (final String rangeValues : rangeHeadersValues) {
			if (rangeValues.startsWith(VALUE_RANGE_PREFIX)) {
				final String rangeValuesWithoutPrefix = rangeValues.substring(
						VALUE_RANGE_PREFIX.length(), rangeValues.length()
				);
				final String[] ranges = rangeValuesWithoutPrefix.split(",");
				for (final String range : ranges) {
					final String[] rangeBorders = range.split(VALUE_RANGE_CONCAT);
					if (rangeBorders.length == 1) {
						sharedStorage.appendObject(containerName, objId, Long.parseLong(rangeBorders[0]), size);
					} else if (rangeBorders.length == 2) {
						final long offset = Long.parseLong(rangeBorders[0]);
						sharedStorage.updateObject(
								containerName, objId, offset, Long.parseLong(rangeBorders[1]) - offset + 1
						);
					} else {
						LOG.warn(
								Markers.ERR, "Invalid range header value: \"{}\"", rangeValues
						);
						return false;
					}

				}
			}
			else {
				LOG.warn(Markers.ERR, "Invalid range header value: \"{}\"", rangeValues);
				return false;
			}
		}
		return true;
	}

	private void handleRead(final String containerName, final String objId,
	                        final Long offset, final ChannelHandlerContext ctx) {
		final HttpResponse response;
		try {
		T obj = sharedStorage.getObject(containerName, objId, offset, 0);
			if (obj != null) {
				final long objSize = obj.getSize();
				ioStats.markRead(true, objSize);
				if (LOG.isTraceEnabled(Markers.MSG)) {
					LOG.trace(Markers.MSG, "Send data object with ID: {}", objId);
				}
				ctx.attr(AttributeKey.<Boolean>valueOf(ctxWriteFlagKey)).set(false);
				response = new DefaultHttpResponse(HTTP_1_1, OK);
				HttpHeaders.setContentLength(response, objSize);
				ctx.write(response);
				if(obj.hasBeenUpdated()) {
					ctx.write(new UpdatedDataItemFileRegion<>(obj));
				} else {
					ctx.write(new DataItemFileRegion<>(obj));
				}
				ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
			} else {
				setHttpResponseStatusInContext(ctx, NOT_FOUND);
				ioStats.markRead(false, 0);
			}
		} catch (final ContainerMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			if(LOG.isTraceEnabled(Markers.ERR)) {
				LOG.trace(Markers.ERR, "No such container: {}", objId);
			}
			ioStats.markRead(false, 0);
		} catch (final ContainerMockException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.WARN, e, "Container \"{}\" failure", containerName);
			ioStats.markRead(false, 0);
		}
	}

	private void handleDelete(final String containerName, final String objId,
	                          final Long offset, final ChannelHandlerContext ctx) {
		try {
			sharedStorage.deleteObject(containerName, objId, offset, -1);
			if (LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(Markers.MSG, "Delete data object with ID: {}", objId);
			}
			ioStats.markDelete(true);
		} catch (ContainerMockNotFoundException e) {
			ioStats.markDelete(false);
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			if (LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(Markers.ERR, "No such container: {}", objId);
			}
		}
	}

	protected void handleGenericContainerReq(final String method, final String containerName,
	                                         final ChannelHandlerContext ctx) {
		switch (method) {
			case HttpRequestConfig.METHOD_HEAD:
				handleContainerExists(containerName, ctx);
				break;
			case HttpRequestConfig.METHOD_PUT:
				handleContainerCreate(containerName);
				break;
			case HttpRequestConfig.METHOD_GET:
				handleContainerList(containerName, ctx);
				break;
			case HttpRequestConfig.METHOD_DELETE:
				handleContainerDelete(containerName);
				break;
		}
	}

	protected abstract void handleContainerList(final String containerName, final ChannelHandlerContext ctx);

	protected final T listContainer(final String containerName, final String marker, final List<T> buff,
	                                final int maxCount) throws ContainerMockException {
		T lastObj = sharedStorage.listObjects(containerName, marker, buff, maxCount);
		if (LOG.isTraceEnabled(Markers.MSG)) {
			LOG.trace(
					Markers.MSG, "Container \"{}\": generated list of {} objects, last one is \"{}\"",
					containerName, buff.size(), lastObj
			);
		}
		return lastObj;
	}

	protected void handleContainerCreate(final String containerName) {
		sharedStorage.createContainer(containerName);
	}

	private void handleContainerExists(final String containerName, final ChannelHandlerContext ctx) {
		if (sharedStorage.getContainer(containerName) == null) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
		}
	}

	private void handleContainerDelete(final String containerName) {
		sharedStorage.deleteContainer(containerName);
	}

	protected void setHttpResponseStatusInContext(final ChannelHandlerContext ctx, final HttpResponseStatus status) {
		ctx.attr(AttributeKey.<HttpResponseStatus>valueOf(responseStatusKey)).set(status);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

}
