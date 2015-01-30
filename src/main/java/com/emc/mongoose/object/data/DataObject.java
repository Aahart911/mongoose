package com.emc.mongoose.object.data;
//
import com.emc.mongoose.base.data.AppendableDataItem;
import com.emc.mongoose.base.data.UpdatableDataItem;
/**
 Created by kurila on 29.09.14.
 Identifiable, appendable and updatable data item.
 Data item identifier is a 64-bit word.
 */
public interface DataObject
extends AppendableDataItem, UpdatableDataItem {
	//
	int ID_RADIX = Character.MAX_RADIX;
    //
    String getId();
    //
    void setId(final String id);
}
