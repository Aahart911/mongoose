package com.emc.mongoose.common.collections;
//
import java.util.List;
/**
 Created by kurila on 22.07.15.
 */
public interface ReusableList<T>
extends List<T>, Reusable<ReusableList<T>> {
}
