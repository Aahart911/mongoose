package com.emc.mongoose.common.logging;
//
//
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.appender.ManagerFactory;
//
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
/** Created by andrey on 13.03.15. */
public final class RunIdFileManager
extends AbstractManager {
	//
	private final String fileName, uriAdvertise;
	private final boolean flagAppend, flagLock, flagBuffered;
	private final int buffSize;
	private final Map<String, OutputStream> outStreamsMap = new HashMap<>();
	private final Layout<? extends Serializable> layout;
	//
	protected RunIdFileManager(
		final String fileName, final boolean flagAppend, final boolean flagLock, final boolean flagBuffered,
		final String uriAdvertise, final Layout<? extends Serializable> layout, final int buffSize
	) {
		super(fileName);
		this.fileName = fileName;
		this.flagAppend = flagAppend;
		this.flagLock = flagLock;
		this.flagBuffered = flagBuffered;
		this.uriAdvertise = uriAdvertise;
		this.layout = layout;
		this.buffSize = buffSize;
	}
	/** Factory Data */
	private static class FactoryData {
		//
		private final boolean flagAppend;
		private final boolean flagLock;
		private final boolean flagBuffered;
		private final int buffSize;
		private final String uriAdvertise;
		private final Layout<? extends Serializable> layout;
		/**
		 * Constructor.
		 * @param flagAppend Append status.
		 * @param flagLock Locking status.
		 * @param flagBuffered Buffering flag.
		 * @param buffSize Buffer size.
		 * @param uriAdvertise the URI to use when advertising the file
		 */
		public FactoryData(
			final boolean flagAppend, final boolean flagLock, final boolean flagBuffered,
			final int buffSize, final String uriAdvertise,
			final Layout<? extends Serializable> layout
		) {
			this.flagAppend = flagAppend;
			this.flagLock = flagLock;
			this.flagBuffered = flagBuffered;
			this.buffSize = buffSize;
			this.uriAdvertise = uriAdvertise;
			this.layout = layout;
		}
	}
	/**
	 * Factory to create a FileManager.
	 */
	private final static class RunIdFileManagerFactory
	implements ManagerFactory<RunIdFileManager, FactoryData> {
		/**
		 * Create a FileManager.
		 * @param fileName The prefix for the name of the File.
		 * @param data The FactoryData
		 * @return The FileManager for the File.
		 */
		@Override
		public RunIdFileManager createManager(final String fileName, final FactoryData data) {
			return new RunIdFileManager(
				fileName, data.flagAppend, data.flagLock, data.flagBuffered,
				data.uriAdvertise, data.layout, data.buffSize
			);
		}
	}
	//
	private final static RunIdFileManagerFactory FACTORY = new RunIdFileManagerFactory();
	//
	public static RunIdFileManager getRunIdFileManager(
		final String fileName,
		final boolean flagAppend, final boolean flagLock, final boolean flagBuffered,
		final String uriAdvertise, final Layout<? extends Serializable> layout, final int buffSize
	) {
		return RunIdFileManager.class.cast(
			getManager(
				fileName, FACTORY,
				new FactoryData(
					flagAppend, flagLock, flagBuffered, buffSize, uriAdvertise, layout
				)
			)
		);
	}
	//
	public final String getFileName() {
		return fileName;
	}
	//
	public final boolean isAppend() {
		return flagAppend;
	}
	//
	public final boolean isLocking() {
		return flagLock;
	}
	//
	public final int getBufferSize() {
		return buffSize;
	}
	//
	protected final synchronized void write(
		final String currRunId, final byte[] buff, final int offset, final int len
	) {
		try(final OutputStream outStream = getOutputStream(currRunId)) {
			outStream.write(buff, offset, len);
		} catch (final IOException e) {
			throw new AppenderLoggingException("Error writing to stream " + getName(), e);
		}
	}
	//
	protected final void write(final String currRunId, final byte[] bytes)  {
		write(currRunId, bytes, 0, bytes.length);
	}
	//
	public final String
		FMT_FILE_PATH = "%s" + File.separator + "%s" + File.separator +"%s",
		FMT_FILE_PATH_NO_RUN_ID = "%s" + File.separator +"%s";
	//
	protected final OutputStream getOutputStream(final String currRunId) {
		OutputStream currentOutPutStream = null;
		File currentOutPutFile;
		if(outStreamsMap.containsKey(currRunId)) {
			currentOutPutStream = outStreamsMap.get(currRunId);
		} else {
			currentOutPutFile = new File(
				currRunId == null ?
					String.format(FMT_FILE_PATH_NO_RUN_ID, LogUtil.PATH_LOG_DIR, fileName) :
					String.format(FMT_FILE_PATH, LogUtil.PATH_LOG_DIR, currRunId, fileName)
			);
			final File parentFile = currentOutPutFile.getParentFile();
			if(parentFile != null && !parentFile.exists()) {
				parentFile.mkdirs();
			}
			//
			try {
				currentOutPutStream = new FileOutputStream(currentOutPutFile.getPath(), flagAppend);
				outStreamsMap.put(currRunId, currentOutPutStream);
			} catch(final IOException e) {
				e.printStackTrace(System.err);
			}
		}
		return currentOutPutStream;
	}
	//
	protected final synchronized void close() {
		for(final OutputStream outStream : outStreamsMap.values()) {
			try {
				outStream.close();
			} catch(final IOException e) {
				e.printStackTrace(System.err);
			}
		}
	}
	/** Flushes all available output streams */
	public final synchronized void flush() {
		for(final OutputStream outStream : outStreamsMap.values()) {
			try {
				outStream.flush();
			} catch(final IOException e) {
				e.printStackTrace(System.err);
			}
		}
	}
}