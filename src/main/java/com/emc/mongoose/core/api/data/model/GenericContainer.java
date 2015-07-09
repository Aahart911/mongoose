package com.emc.mongoose.core.api.data.model;
//
import com.emc.mongoose.core.api.data.DataItem;
/**
 Created by kurila on 03.07.15.
 */
public interface GenericContainer<T extends DataItem> {
	//
	String getName();
	//
	boolean exists(final String addr)
	throws IllegalStateException;
	//
	void create(final String addr)
	throws IllegalStateException;
	//
	void delete(final String addr)
	throws IllegalStateException;
	//
}