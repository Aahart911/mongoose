package com.emc.mongoose.core.impl.item.base;
//
import com.emc.mongoose.common.conf.AppConifg;
import com.emc.mongoose.common.conf.BasicValueGenerator;
//
import com.emc.mongoose.common.math.MathUtil;
import com.emc.mongoose.common.net.ServiceUtil;
/**
 Created by kurila on 18.12.15.
 */
public class ItemIdGenerator
extends BasicValueGenerator<Long> {
	//
	protected final AppConifg.ItemNamingType namingType;
	//
	public ItemIdGenerator(final AppConifg.ItemNamingType namingType) {
		this(
			namingType,
			Math.abs(
				Long.reverse(System.currentTimeMillis()) ^
				Long.reverseBytes(System.nanoTime()) ^
				ServiceUtil.getHostAddrCode()
			)
		);
	}
	//
	public ItemIdGenerator(final AppConifg.ItemNamingType namingType, final long initialValue) {
		super(initialValue, null);
		this.namingType = namingType;
	}
	/**
	 INTENDED ONLY FOR SEQUENTIAL USE. NOT THREAD-SAFE!
	 @return next id
	 */
	@Override
	public Long get() {
		switch(namingType) {
			case RANDOM:
				return lastValue = Math.abs(MathUtil.xorShift(lastValue ^ System.nanoTime()));
			case ASC:
				return lastValue ++;
			case DESC:
				return lastValue --;
			default:
				return 0L;
		}
	}
}
