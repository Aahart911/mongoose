package com.emc.mongoose.core.api.item.base;
import java.io.Externalizable;
/**
 Created by kurila on 20.10.15.
 */
public interface Item
extends Externalizable {

	String SLASH = "/";

	String getPath();
	void setPath(final String path);

	String getName();
	void setName(final String name);

}
