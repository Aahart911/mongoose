package com.emc.mongoose.core.impl.item.data;
// mongoose-common
import com.emc.mongoose.common.conf.BasicConfig;
import com.emc.mongoose.common.log.Markers;
// mongoose-core-api
import com.emc.mongoose.core.api.item.data.ContentSource;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.ByteBuffer;
/**
 Created by kurila on 23.07.14.
 A uniform data source for producing uniform data items.
 Implemented as finite buffer of pseudorandom bytes.
 */
public final class UniformContentSource
extends ContentSourceBase
implements ContentSource {
	//
	private final static Logger LOG = LogManager.getLogger();
	////////////////////////////////////////////////////////////////////////////////////////////////
	public UniformContentSource()
	throws NumberFormatException {
		this(
			Long.parseLong(BasicConfig.getContext().getDataRingSeed(), 0x10),
			(int) BasicConfig.getContext().getDataRingSize()
		);
	}
	//
	public UniformContentSource(final long seed, final int size) {
		super(ByteBuffer.allocateDirect(size));
		this.seed = seed;
		generateData(zeroByteLayer, seed);
		LOG.debug(Markers.MSG, "New ring buffer instance #{}", hashCode());
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	// Human readable "serialization" implementation ///////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final String toString() {
		return
			Long.toHexString(seed) + BasicConfig.LIST_SEP +
			Integer.toHexString(zeroByteLayer.capacity());
	}
	//
	public static UniformContentSource fromString(final String metaInfo)
		throws IllegalArgumentException, IOException {
		final String values[] = metaInfo.split(BasicConfig.LIST_SEP);
		if(values.length == 2) {
			return new UniformContentSource(
				Long.parseLong(values[0], 0x10), Integer.parseInt(values[1], 0x10)
			);
		} else {
			throw new IllegalArgumentException();
		}
	}
}
