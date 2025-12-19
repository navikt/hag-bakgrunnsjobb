package no.nav.hag.utils.bakgrunnsjobb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class RecurringJob(
    private val coroutineScope: CoroutineScope,
    private val waitMillisBetweenRuns: Long,
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    protected var isRunning = false

    fun startAsync(retryOnFail: Boolean = false) {
        logger.info("Starter opp")
        isRunning = true
        scheduleAsyncJobRun(retryOnFail)
    }

    private fun scheduleAsyncJobRun(retryOnFail: Boolean) {
        coroutineScope.launch {
            delay(waitMillisBetweenRuns)
            while (isRunning) {
                runCatching {
                    doJob()
                }.getOrElse {
                    if (retryOnFail) {
                        logger.error("Jobben feilet, men forsøker på nytt etter ${waitMillisBetweenRuns / 1000} sek", it)
                    } else {
                        isRunning = false
                        throw it
                    }
                }
                delay(waitMillisBetweenRuns)
            }
            logger.info("Stoppet.")
        }
    }

    fun stop() {
        logger.debug("Stopper jobben...")
        isRunning = false
    }

    abstract fun doJob()
}
