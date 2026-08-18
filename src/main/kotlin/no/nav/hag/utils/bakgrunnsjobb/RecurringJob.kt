package no.nav.hag.utils.bakgrunnsjobb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration

abstract class RecurringJob(
    private val interval: Duration,
    private val coroutineScope: CoroutineScope,
) {
    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    var isRunning = false
        private set

    fun startAsync(retryOnFail: Boolean = false) {
        logger.info("Starter opp.")
        isRunning = true
        scheduleAsyncJobRun(retryOnFail)
    }

    private fun scheduleAsyncJobRun(retryOnFail: Boolean) {
        coroutineScope.launch {
            delay(interval)
            while (isRunning) {
                runCatching {
                    doJob()
                }.getOrElse {
                    if (retryOnFail) {
                        logger.error("Jobben feilet, men forsøker på nytt etter ${interval.inWholeSeconds} sek.", it)
                    } else {
                        isRunning = false
                        throw it
                    }
                }
                delay(interval)
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
