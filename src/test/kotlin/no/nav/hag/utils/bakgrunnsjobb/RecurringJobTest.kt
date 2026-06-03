package no.nav.hag.utils.bakgrunnsjobb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringJobTest {
    private val testCoroutineScope = TestScope()

    val interval = 100.milliseconds
    val job = TestRecurringJob(testCoroutineScope, interval)

    @Test
    fun `StartAsync does job in coroutine and then waits`() {
        assertThat(job.getJobCompletedCounter()).isEqualTo(0)
        job.startAsync()
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        assertThat(job.getJobCompletedCounter()).isEqualTo(0)

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(interval)
            runCurrent()
        }

        assertThat(job.getJobCompletedCounter()).isEqualTo(1)
    }

    @Test
    fun `When job fails and retry is on, ignore errors and run job again`() {
        job.failOnJob = true
        job.startAsync(retryOnFail = true)
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        assertThat(job.getCallCounter()).isEqualTo(0)
        assertThat(job.getJobCompletedCounter()).isEqualTo(0)

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(interval)
            runCurrent()
        }

        assertThat(job.getCallCounter()).isEqualTo(1)
        assertThat(job.getJobCompletedCounter()).isEqualTo(0)
    }

    @Test
    fun `When job fails and retry is off, stop processing`() {
        job.failOnJob = true
        job.startAsync(retryOnFail = false)
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(1)
            runCurrent()
        }

        assertThat(job.getCallCounter()).isEqualTo(0)

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(interval)
            runCurrent()
        }

        assertThat(job.getCallCounter()).isEqualTo(1)
        assertThat(job.getJobCompletedCounter()).isEqualTo(0)
    }

    @Test
    fun `Stopping the job prevents new execution`() {
        job.startAsync()
        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(interval)
            runCurrent()
        }
        job.stop()

        assertThat(job.getJobCompletedCounter()).isEqualTo(1)

        testCoroutineScope.testScheduler.apply {
            advanceTimeBy(interval)
            runCurrent()
        }

        assertThat(job.getJobCompletedCounter()).isEqualTo(1)
    }

    class TestRecurringJob(
        coroutineScope: CoroutineScope,
        interval: Duration,
    ) : RecurringJob(interval, coroutineScope) {
        var failOnJob: Boolean = false
        private var jobCompletedCounter = 0

        fun getJobCompletedCounter(): Int = jobCompletedCounter

        private var callCounter = 0

        fun getCallCounter(): Int = callCounter

        override fun doJob() {
            callCounter++
            if (failOnJob) throw IOException()
            jobCompletedCounter++
        }
    }
}
