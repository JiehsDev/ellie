package com.example.jikan.coach

import com.example.jikan.data.AiInsightDao
import com.example.jikan.data.AiInsightEntity
import com.example.jikan.screentime.AppUsageStatus
import com.example.jikan.screentime.ExceededAppUsage
import com.example.jikan.screentime.ScreenTimeAppUsage
import com.example.jikan.screentime.ScreenTimeSummary
import com.example.jikan.screentime.UsageChange
import com.example.jikan.screentime.UsageChangeDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7: the local model is optional. These tests prove every failure mode
 * — unavailable, timeout, inference failure, malformed output, empty output —
 * resolves to a deterministic fallback message, and that healthy output is
 * sanitized before use. The llama.cpp runtime itself is not under test here.
 */
class ScreenTimeAiTest {

    private class ScriptedEngine(
        var ready: Boolean = true,
        var generateBehavior: suspend (String, Int) -> String = { _, _ -> "model says hi." },
        var initializeThrows: Boolean = false,
    ) : InferenceEngine {
        var lastPrompt: String? = null
        var lastMaxTokens: Int = 0

        override suspend fun initialize() {
            if (initializeThrows) throw IllegalStateException("no model file")
        }

        override suspend fun generate(prompt: String, maxTokens: Int): String {
            lastPrompt = prompt
            lastMaxTokens = maxTokens
            return generateBehavior(prompt, maxTokens)
        }

        override fun isReady(): Boolean = ready
        override fun release() = Unit
    }

    private val dao = object : AiInsightDao {
        override suspend fun insert(insight: AiInsightEntity) = Unit
        override suspend fun getForSession(sessionId: Long): AiInsightEntity? = null
        override fun observeAll(): Flow<List<AiInsightEntity>> = flowOf(emptyList())
    }

    private fun context(
        total: Int = 201,
        yesterday: Int = 224,
        usageDelta: Int = -23,
        topApps: List<ScreenTimeAppUsage> = listOf(
            ScreenTimeAppUsage("com.youtube", "YouTube", 71, 35.3f, AppUsageStatus.GENERAL)
        ),
        exceededApps: List<ExceededAppUsage> = emptyList(),
    ) = ScreenTimeSummary(
        epochDay = 10L,
        totalScreenTimeMinutes = total,
        previousDayScreenTimeMinutes = yesterday,
        averageDailyScreenTimeMinutes = 190,
        restrictedAppMinutes = 134,
        earningAppMinutes = 31,
        earnedMinutes = 15,
        spentMinutes = 20,
        walletBalanceMinutes = 10,
        topApps = topApps,
        exceededApps = exceededApps,
        recentUsageChange = UsageChange(usageDelta, null, UsageChangeDirection.DOWN),
    )

    private fun deterministic(context: ScreenTimeContext, protection: Boolean): String =
        runBlocking { FakeAiCoach().insightForScreenTime(context, protection) }

    // --- FakeAiCoach: always deterministic ---

    @Test
    fun `fake coach answers without any model`() = runBlocking {
        val message = FakeAiCoach().insightForScreenTime(context(), protectionEnabled = true)

        // delta -23 -> UsageImproved(23)
        assertEquals(
            "You've used 23 fewer minutes than yesterday. Quiet progress — I noticed.",
            message,
        )
    }

    @Test
    fun `fake coach reports an exceeded limit`() = runBlocking {
        val ctx = context(
            exceededApps = listOf(ExceededAppUsage("com.tiktok", "TikTok", 42, 30, 12))
        )

        val message = FakeAiCoach().insightForScreenTime(ctx, protectionEnabled = true)

        assertEquals(
            "TikTok passed its 30-minute limit by 12 minutes. The guard is holding; I'm just keeping score.",
            message,
        )
    }

    @Test
    fun `fake coach reports protection off`() = runBlocking {
        val message = FakeAiCoach().insightForScreenTime(context(), protectionEnabled = false)

        assertEquals(
            "Protection is off right now. Jikan won't be able to enforce your limits until you turn it back on.",
            message,
        )
    }

    // --- RealAiCoach: healthy path ---

    @Test
    fun `real coach uses healthy model output and sends the facts prompt`() = runBlocking {
        val engine = ScriptedEngine(generateBehavior = { _, _ ->
            "You've used less screen time than yesterday, and YouTube is doing most of today's heavy lifting."
        })
        val coach = RealAiCoach(engine, dao)
        val ctx = context()

        val result = coach.insightForScreenTime(ctx, protectionEnabled = true)

        assertEquals(
            "You've used less screen time than yesterday, and YouTube is doing most of today's heavy lifting.",
            result,
        )
        // Structured context only: the exact system line plus the facts.
        assertTrue(engine.lastPrompt!!.contains("You are Jikan Coach, a calm screen-time companion."))
        assertTrue(engine.lastPrompt!!.contains("Total today: 201 minutes"))
        assertTrue(engine.lastPrompt!!.contains("Protection: on"))
        assertTrue(engine.lastMaxTokens > 0)
    }

    @Test
    fun `real coach forwards the protection state into the prompt`() = runBlocking {
        val engine = ScriptedEngine()
        val coach = RealAiCoach(engine, dao)

        coach.insightForScreenTime(context(), protectionEnabled = false)

        assertTrue(engine.lastPrompt!!.contains("Protection: off"))
    }

    // --- RealAiCoach: all five failure modes -> deterministic fallback ---

    @Test
    fun `real coach falls back when generate throws`() = runBlocking {
        val engine = ScriptedEngine(generateBehavior = { _, _ -> throw RuntimeException("boom") })
        val coach = RealAiCoach(engine, dao)
        val ctx = context()

        assertEquals(deterministic(ctx, true), coach.insightForScreenTime(ctx, true))
    }

    @Test
    fun `real coach falls back when the model is unavailable`() = runBlocking {
        val engine = ScriptedEngine(ready = false, initializeThrows = true)
        val coach = RealAiCoach(engine, dao)
        val ctx = context()

        assertEquals(deterministic(ctx, true), coach.insightForScreenTime(ctx, true))
    }

    @Test
    fun `real coach falls back on timeout`() = runBlocking {
        val engine = ScriptedEngine(generateBehavior = { _, _ ->
            delay(5_000)
            "too late."
        })
        val coach = RealAiCoach(engine, dao, inferenceTimeoutMs = 200L)
        val ctx = context()

        assertEquals(deterministic(ctx, true), coach.insightForScreenTime(ctx, true))
    }

    @Test
    fun `real coach falls back on empty output`() = runBlocking {
        val engine = ScriptedEngine(generateBehavior = { _, _ -> "   \n  " })
        val coach = RealAiCoach(engine, dao)
        val ctx = context()

        assertEquals(deterministic(ctx, true), coach.insightForScreenTime(ctx, true))
    }

    @Test
    fun `real coach falls back on prompt echo`() = runBlocking {
        val engine = ScriptedEngine(generateBehavior = { _, _ ->
            "Screen-time facts:\nTotal today: 201 minutes\nWrite the insight now."
        })
        val coach = RealAiCoach(engine, dao)
        val ctx = context()

        assertEquals(deterministic(ctx, true), coach.insightForScreenTime(ctx, true))
    }

    @Test
    fun `real coach initializes a non-ready engine before generating`() = runBlocking {
        val engine = ScriptedEngine(ready = false, initializeThrows = false)
        var initialized = false
        val tracking = object : InferenceEngine by engine {
            override suspend fun initialize() {
                initialized = true
            }

            override fun isReady(): Boolean = initialized
        }
        val coach = RealAiCoach(tracking, dao)

        coach.insightForScreenTime(context(), protectionEnabled = true)

        assertTrue(initialized)
    }

    // --- InsightSanitizer ---

    @Test
    fun `sanitizer passes clean output through`() {
        assertEquals("Hello world.", InsightSanitizer.sanitize("  Hello world.  "))
    }

    @Test
    fun `sanitizer rejects null and blank`() {
        assertNull(InsightSanitizer.sanitize(null))
        assertNull(InsightSanitizer.sanitize("   \n "))
    }

    @Test
    fun `sanitizer keeps at most two sentences`() {
        val raw = "First sentence here. Second sentence here. Third one rambles on."

        assertEquals("First sentence here. Second sentence here.", InsightSanitizer.sanitize(raw))
    }

    @Test
    fun `sanitizer strips prompt echo lines`() {
        val raw = "Screen-time facts:\nTotal today: 201 minutes\nReal insight here."

        assertEquals("Real insight here.", InsightSanitizer.sanitize(raw))
    }

    @Test
    fun `sanitizer caps runaway length`() {
        val raw = "a".repeat(500)

        val cleaned = InsightSanitizer.sanitize(raw)!!
        assertTrue(cleaned.length <= 280)
    }
}
