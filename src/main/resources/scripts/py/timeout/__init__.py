from java.lang import IllegalArgumentException, Long
from java.util import NoSuchElementException
#
from org.apache.logging.log4j import LogManager
#
from com.emc.mongoose.common.conf import RunTimeConfig
from com.emc.mongoose.common.logging import LogUtil
#
LOG = LogManager.getLogger()
#
def init():
	#
	runTime = None  # tuple of (value, unit)
	try:
		localConfig = RunTimeConfig.getContext()
		runTimeValue = localConfig.getLoadLimitTimeValue()
		if runTimeValue <= 0:
			runTimeValue = Long.MAX_VALUE
		runTime = runTimeValue, localConfig.getLoadLimitTimeUnit()
	except NoSuchElementException:
		LOG.error(LogUtil.ERR, "No timeout specified, try arg -Drun.time=<INTEGER>.<UNIT> to override")
	except IllegalArgumentException as e:
		e.printStackTrace()
		LOG.error(LogUtil.ERR, "Timeout unit should be a name of a constant from TimeUnit enumeration")
	except IndexError:
		LOG.error(LogUtil.ERR, "Time unit should be specified with timeout value (following after \".\" separator)")
	return runTime