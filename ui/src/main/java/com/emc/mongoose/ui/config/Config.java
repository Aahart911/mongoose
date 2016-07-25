package com.emc.mongoose.ui.config;

import com.emc.mongoose.model.api.data.ContentSource;
import com.emc.mongoose.model.util.SizeInBytes;
import com.emc.mongoose.model.util.TimeUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 Created on 11.07.16.
 */
@SuppressWarnings("unused")
public final class Config {

	private static final class TimeStrToLongDeserializer
	extends JsonDeserializer<Long> {

		@SuppressWarnings("DuplicateThrows")
		@Override
		public final Long deserialize(final JsonParser p, final DeserializationContext ctx)
		throws JsonProcessingException, IOException {
			final String rawValue = p.getValueAsString();
			final TimeUnit timeUnit = TimeUtil.getTimeUnit(rawValue);
			if(timeUnit == null) {
				return TimeUtil.getTimeValue(rawValue);
			} else {
				return timeUnit.toSeconds(TimeUtil.getTimeValue(rawValue));
			}
		}
	}

	private static final class SizeInBytesDeserializer
	extends JsonDeserializer<SizeInBytes> {
		@SuppressWarnings("DuplicateThrows")
		@Override
		public final SizeInBytes deserialize(final JsonParser p, final DeserializationContext ctx)
		throws JsonProcessingException, IOException{
			return new SizeInBytes(p.getValueAsString());
		}
	}

	private static final class DataRangesConfigDeserializer
		extends JsonDeserializer<DataRangesConfig> {
		@SuppressWarnings("DuplicateThrows")
		@Override
		public final DataRangesConfig deserialize(final JsonParser p, final DeserializationContext ctx)
		throws JsonProcessingException, IOException{
			try {
				return new DataRangesConfig(p.getValueAsInt());
			} catch(final IOException ignored) {
				return new DataRangesConfig(p.getValueAsString());
			}
		}
	}



	private static final String KEY_NAME = "name";
	private static final String KEY_VERSION = "version";
	private static final String KEY_IO = "io";
	private static final String KEY_SOCKET = "socket";
	private static final String KEY_ITEM = "item";
	private static final String KEY_LOAD = "load";
	private static final String KEY_RUN = "run";
	private static final String KEY_STORAGE = "storage";
	private static final String KEY_ALIASING = "aliasing";

	@JsonProperty(KEY_NAME) private String name;
	@JsonProperty(KEY_VERSION) private String version;
	@JsonProperty(KEY_IO) private IoConfig ioConfig;
	@JsonProperty(KEY_SOCKET) private SocketConfig socketConfig;
	@JsonProperty(KEY_STORAGE) private StorageConfig storageConfig;
	@JsonProperty(KEY_LOAD) private LoadConfig loadConfig;
	@JsonProperty(KEY_RUN) private RunConfig runConfig;
	@JsonProperty(KEY_ITEM) private ItemConfig itemConfig;
	@JsonProperty(KEY_ALIASING) private Map<String, Object> aliasingConfig;

	public Config() {}

	public final String getName() {
		return name;
	}

	public final String getVersion() {
		return version;
	}

	public final IoConfig getIoConfig() {
		return ioConfig;
	}

	public final SocketConfig getSocketConfig() {
		return socketConfig;
	}

	public final StorageConfig getStorageConfig() {
		return storageConfig;
	}

	public final LoadConfig getLoadConfig() {
		return loadConfig;
	}

	public final RunConfig getRunConfig() {
		return runConfig;
	}

	public final ItemConfig getItemConfig() {
		return itemConfig;
	}

	public final Map<String, Object> getAliasingConfig() {
		return aliasingConfig;
	}

	public final static class IoConfig {

		static final String KEY_BUFFER = "buffer";

		@JsonProperty(KEY_BUFFER)
		private BufferConfig bufferConfig;

		public IoConfig() {
		}

		public static class BufferConfig {

			static final String KEY_SIZE = "size";

			@JsonProperty(KEY_SIZE) @JsonDeserialize(using = SizeInBytesDeserializer.class)
			private SizeInBytes size;

			public BufferConfig() {
			}

			public final SizeInBytes getSize() {
				return size;
			}
		}

		public final BufferConfig getBufferConfig() {
			return bufferConfig;
		}
	}

	public final static class SocketConfig {

		static final String KEY_TIMEOUT_MILLISEC = "timeoutMilliSec";
		static final String KEY_REUSE_ADDR = "reuseAddr";
		static final String KEY_KEEP_ALIVE = "keepAlive";
		static final String KEY_TCP_NO_DELAY = "tcpNoDelay";
		static final String KEY_LINGER = "linger";
		static final String KEY_BIND_BACKLOG_SIZE = "bindBacklogSize";
		static final String KEY_INTEREST_OP_QUEUED = "interestOpQueued";
		static final String KEY_SELECT_INTERVAL = "selectInterval";

		@JsonProperty(KEY_TIMEOUT_MILLISEC) private int timeoutMilliSec;
		@JsonProperty(KEY_REUSE_ADDR) private boolean reuseAddr;
		@JsonProperty(KEY_KEEP_ALIVE) private boolean keepAlive;
		@JsonProperty(KEY_TCP_NO_DELAY) private boolean tcpNoDelay;
		@JsonProperty(KEY_LINGER) private int linger;
		@JsonProperty(KEY_BIND_BACKLOG_SIZE) private int bindBackLogSize;
		@JsonProperty(KEY_INTEREST_OP_QUEUED) private boolean interestOpQueued;
		@JsonProperty(KEY_SELECT_INTERVAL) private int selectInterval;

		public SocketConfig() {}

		public final int getTimeoutInMilliseconds() {
			return timeoutMilliSec;
		}

		public final boolean getReusableAddress() {
			return reuseAddr;
		}

		public final boolean getKeepAlive() {
			return keepAlive;
		}

		public final boolean getTcpNoDelay() {
			return tcpNoDelay;
		}

		public final int getLinger() {
			return linger;
		}

		public final int getBindBackLogSize() {
			return bindBackLogSize;
		}

		public final boolean getInterestOpQueued() {
			return interestOpQueued;
		}

		public final int getSelectInterval() {
			return selectInterval;
		}
	}

	public final static class ItemConfig {

		static final String KEY_TYPE = "type";
		static final String KEY_DATA = "data";
		static final String KEY_INPUT = "input";
		static final String KEY_OUTPUT = "output";
		static final String KEY_NAMING = "naming";

		@JsonProperty(KEY_TYPE) private String type;
		@JsonProperty(KEY_DATA) private DataConfig dataConfig;
		@JsonProperty(KEY_INPUT) private InputConfig input;
		@JsonProperty(KEY_OUTPUT) private OutputConfig output;
		@JsonProperty(KEY_NAMING) private NamingConfig namingConfig;

		public ItemConfig() {
		}

		public final String getType() {
			return type;
		}

		public final DataConfig getDataConfig() {
			return dataConfig;
		}

		public final InputConfig getInputConfig() {
			return input;
		}

		public final OutputConfig getOutputConfig() {
			return output;
		}

		public final NamingConfig getNamingConfig() {
			return namingConfig;
		}

		public final static class DataConfig {

			static final String KEY_CONTENT = "content";
			static final String KEY_RANGES = "ranges";
			static final String KEY_SIZE = "size";
			static final String KEY_VERIFY = "verify";
			
			@JsonProperty(KEY_CONTENT) private ContentConfig contentConfig;
			@JsonProperty(KEY_RANGES) @JsonDeserialize(using = DataRangesConfigDeserializer.class)
			private DataRangesConfig ranges;
			@JsonProperty(KEY_SIZE) @JsonDeserialize(using = SizeInBytesDeserializer.class)
			private SizeInBytes size;
			@JsonProperty(KEY_VERIFY) private boolean verify;

			public DataConfig() {
			}

			public ContentConfig getContentConfig() {
				return contentConfig;
			}

			public final DataRangesConfig getRanges() {
				return ranges;
			}

			public final SizeInBytes getSize() {
				return size;
			}

			public final boolean getVerify() {
				return verify;
			}

			public final static class ContentConfig {

				static final String KEY_FILE = "file";
				static final String KEY_SEED = "seed";
				static final String KEY_RING_SIZE = "ringSize";
				
				@JsonProperty(KEY_FILE) private String file;
				@JsonProperty(KEY_SEED) private String seed;

				@JsonProperty(KEY_RING_SIZE) @JsonDeserialize(using = SizeInBytesDeserializer.class)
				private SizeInBytes ringSize;

				public ContentConfig() {
				}

				public final String getFile() {
					return file;
				}

				public final String getSeed() {
					return seed;
				}

				public final SizeInBytes getRingSize() {
					return ringSize;
				}
			}
		}

		public final static class InputConfig {

			static final String KEY_CONTAINER = "container";
			static final String KEY_FILE = "file";

			@JsonProperty(KEY_CONTAINER) private String container;
			@JsonProperty(KEY_FILE) private String file;

			public InputConfig() {
			}

			public final String getContainer() {
				return container;
			}

			public final String getFile() {
				return file;
			}

		}

		public final static class OutputConfig {

			static final String KEY_CONTAINER = "container";
			static final String KEY_FILE = "file";

			@JsonProperty(KEY_CONTAINER) private String container;
			@JsonProperty(KEY_FILE) private String file;

			public OutputConfig() {
			}

			public String getContainer() {
				return container;
			}

			public String getFile() {
				return file;
			}
		}
		
		public final static class NamingConfig {

			static final String KEY_TYPE = "type";
			static final String KEY_PREFIX = "prefix";
			static final String KEY_RADIX = "radix";
			static final String KEY_OFFSET = "offset";
			static final String KEY_LENGTH = "length";
			
			@JsonProperty(KEY_TYPE) private String type;
			@JsonProperty(KEY_PREFIX) private String prefix;
			@JsonProperty(KEY_RADIX) private int radix;
			@JsonProperty(KEY_OFFSET) private long offset;
			@JsonProperty(KEY_LENGTH) private int length;

			public NamingConfig() {
			}

			public final String getType() {
				return type;
			}

			public final String getPrefix() {
				return prefix;
			}

			public final int getRadix() {
				return radix;
			}

			public final long getOffset() {
				return offset;
			}

			public final int getLength() {
				return length;
			}
		}
	}

	public final static class LoadConfig {

		static final String KEY_CIRCULAR = "circular";
		static final String KEY_TYPE = "type";
		static final String KEY_CONCURRENCY = "concurrency";
		static final String KEY_LIMIT = "limit";
		static final String KEY_METRICS = "metrics";
		
		@JsonProperty(KEY_CIRCULAR) private boolean circular;
		@JsonProperty(KEY_TYPE) private String type;
		@JsonProperty(KEY_CONCURRENCY) private int concurrency;
		@JsonProperty(KEY_LIMIT) private LimitConfig limitConfig;
		@JsonProperty(KEY_METRICS) private MetricsConfig metricsConfig;

		public LoadConfig() {
		}

		public final String getType() {
			return type;
		}

		public final boolean getCircular() {
			return circular;
		}

		public int getConcurrency() {
			return concurrency;
		}

		public final LimitConfig getLimitConfig() {
			return limitConfig;
		}

		public final MetricsConfig getMetricsConfig() {
			return metricsConfig;
		}

		public final static class LimitConfig {

			static final String KEY_COUNT = "count";
			static final String KEY_RATE = "rate";
			static final String KEY_SIZE = "size";
			static final String KEY_TIME = "time";

			@JsonProperty(KEY_COUNT) private long count;
			@JsonProperty(KEY_RATE) private double rate;
			@JsonProperty(KEY_SIZE) private int size;

			@JsonDeserialize(using = TimeStrToLongDeserializer.class) @JsonProperty(KEY_TIME)
			private long time;

			public LimitConfig() {
			}

			public final long getCount() {
				return count;
			}

			public final double getRate() {
				return rate;
			}

			public final int getSize() {
				return size;
			}

			public final long getTime() {
				return time;
			}
		}

		public final static class MetricsConfig {

			static final String KEY_INTERMEDIATE = "intermediate";
			static final String KEY_PERIOD = "period";
			static final String KEY_PRECONDITION= "precondition";
			
			@JsonProperty(KEY_INTERMEDIATE) private boolean intermediate;

			@JsonDeserialize(using = TimeStrToLongDeserializer.class) @JsonProperty(KEY_PERIOD)
			private long period;

			@JsonProperty(KEY_PRECONDITION) private boolean precondition;

			public MetricsConfig() {
			}

			public final boolean getIntermediate() {
				return intermediate;
			}

			public final long getPeriod() {
				return period;
			}

			public final boolean getPrecondition() {
				return precondition;
			}
		}
	}

	public final static class RunConfig {

		static final String KEY_FILE = "file";
		static final String KEY_ID = "id";
		
		@JsonProperty(KEY_FILE) private String file;
		@JsonProperty(KEY_ID) private String id;

		public RunConfig() {
		}

		public final String getFile() {
			return file;
		}

		public final String getId() {
			return id;
		}
	}

	public final static class StorageConfig {

		static final String KEY_ADDRS = "addrs";
		static final String KEY_AUTH = "auth";
		static final String KEY_HTTP = "http";
		static final String KEY_PORT = "port";
		static final String KEY_SSL = "ssl";
		static final String KEY_TYPE = "type";
		static final String KEY_MOCK = "mock";

		@JsonProperty(KEY_ADDRS) private List<String> addrs;
		@JsonProperty(KEY_AUTH) private AuthConfig authConfig;
		@JsonProperty(KEY_HTTP) private HttpConfig httpConfig;
		@JsonProperty(KEY_PORT) private int port;
		@JsonProperty(KEY_SSL) private boolean ssl;
		@JsonProperty(KEY_TYPE) private String type;
		@JsonProperty(KEY_MOCK) private MockConfig mockConfig;

		public StorageConfig() {
		}

		public List<String> getAddresses() {
			return addrs;
		}

		public AuthConfig getAuthConfig() {
			return authConfig;
		}

		public HttpConfig getHttpConfig() {
			return httpConfig;
		}

		public int getPort() {
			return port;
		}

		public boolean isSsl() {
			return ssl;
		}

		public String getType() {
			return type;
		}

		public MockConfig getMockConfig() {
			return mockConfig;
		}

		public final static class AuthConfig {

			static final String KEY_ID = "id";
			static final String KEY_SECRET = "secret";
			static final String KEY_TOKEN = "token";
			
			@JsonProperty(KEY_ID) private String id;
			@JsonProperty(KEY_SECRET) private String secret;
			@JsonProperty(KEY_TOKEN) private String token;

			public AuthConfig() {
			}

			public String getId() {
				return id;
			}

			public String getSecret() {
				return secret;
			}

			public String getToken() {
				return token;
			}
		}

		public final static class HttpConfig {

			static final String KEY_API = "api";
			static final String KEY_FS_ACCESS = "fsAccess";
			static final String KEY_HEADERS = "headers";
			public static final String KEY_HEADER_CONNECTION = "Connection";
			public static final String KEY_HEADER_USER_AGENT = "User-Agent";
			static final String KEY_NAMESPACE = "namespace";
			static final String KEY_VERSIONING = "versioning";
			
			@JsonProperty(KEY_API) private String api;
			@JsonProperty(KEY_FS_ACCESS) private boolean fsAccess;
			@JsonProperty(KEY_NAMESPACE) private String namespace;
			@JsonProperty(KEY_VERSIONING) private boolean versioning;
			@JsonProperty(KEY_HEADERS) private Map<String, String> headers;

			public HttpConfig() {
			}

			public String getApi() {
				return api;
			}

			public boolean getFsAccess() {
				return fsAccess;
			}

			public String getNamespace() {
				return namespace;
			}

			public boolean getVersioning() {
				return versioning;
			}

			public Map<String, String> getHeaders() {
				return headers;
			}
		}

		public final static class MockConfig {

			static final String KEY_HEAD_COUNT = "headCount";
			static final String KEY_CAPACITY = "capacity";
			static final String KEY_CONTAINER = "container";
			
			@JsonProperty(KEY_HEAD_COUNT) private int headCount;
			@JsonProperty(KEY_CAPACITY) private int capacity;
			@JsonProperty(KEY_CONTAINER) private ContainerConfig containerConfig;

			public MockConfig() {
			}

			public int getHeadCount() {
				return headCount;
			}

			public int getCapacity() {
				return capacity;
			}

			public ContainerConfig getContainerConfig() {
				return containerConfig;
			}

			public final static class ContainerConfig {

				static final String KEY_CAPACITY = "capacity";
				static final String KEY_COUNT_LIMIT = "countLimit";
				
				@JsonProperty(KEY_CAPACITY) private int capacity;
				@JsonProperty(KEY_COUNT_LIMIT) private int countLimit;

				public ContainerConfig() {
				}

				public int getCapacity() {
					return capacity;
				}

				public int getCountLimit() {
					return countLimit;
				}
			}

		}

	}
}
