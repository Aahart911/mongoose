package com.emc.mongoose.core.impl.item.data;
//
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.item.data.ContentSource;
//
/**
 Created by andrey on 22.11.15.
 */
public class BasicFile
extends BasicMutableDataItem
implements FileItem {
	////////////////////////////////////////////////////////////////////////////////////////////////
	public BasicFile() {
		super();
		name = Long.toString(offset, Character.MAX_RADIX);
	}
	//
	public BasicFile(final ContentSource contentSrc) {
		super(contentSrc); // ranges remain uninitialized
		name = Long.toString(offset, Character.MAX_RADIX);
	}
	//
	public BasicFile(final String value, final ContentSource contentSrc) {
		super(value, contentSrc);
	}
	//
	public BasicFile(final Long offset, final Long size, final ContentSource contentSrc) {
		super(offset, size, contentSrc);
	}
	//
	public BasicFile(
		final String name, final Long offset, final Long size, final ContentSource contentSrc
	) {
		super(name, offset, size, contentSrc);
	}
	//
	public BasicFile(
		final String path, final String name, final Long offset, final Long size,
		final ContentSource contentSrc
	) {
		super(path, name, offset, size, contentSrc);
	}//
	public BasicFile(
		final String path, final String name, final Long offset, final Long size,
		final Integer layerNum, final ContentSource contentSrc
	) {
		super(path, name, offset, size, layerNum, contentSrc);
	}
}
