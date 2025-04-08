import kotlinx.datetime.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class LocalDateTimeRange(
    override val start: LocalDateTime,
    override val endInclusive: LocalDateTime
) : ClosedRange<LocalDateTime>, Iterable<LocalDateTime> {

    fun LocalDateTime.plus(duration: Duration, timeZone: TimeZone): LocalDateTime {
        // 将 LocalDateTime 转换为 Instant（需要指定时区）
        val instant = this.toInstant(timeZone)
        // 对 Instant 添加时间间隔
        val newInstant = instant.plus(duration)
        // 转换回 LocalDateTime（需要指定同一时区）
        return newInstant.toLocalDateTime(timeZone)
    }



    override fun iterator(): Iterator<LocalDateTime> {
        // 默認步長改為 1 天
        return LocalDateTimeProgression(start, endInclusive, 1.days).iterator()
    }

    override fun contains(value: LocalDateTime): Boolean {
        return value >= start && value <= endInclusive
    }

    override fun isEmpty(): Boolean = start > endInclusive
}

operator fun LocalDateTime.rangeTo(that: LocalDateTime): LocalDateTimeRange {
    return LocalDateTimeRange(this, that)
}

// --- Step 3 & 4 & 5: Progression, Step Function, and Iterator ---

data class LocalDateTimeProgression(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
    val stepDuration: Duration
) : Iterable<LocalDateTime> {

    override fun iterator(): Iterator<LocalDateTime> {
        return LocalDateTimeIterator(start, endInclusive, stepDuration)
    }

    private class LocalDateTimeIterator(
        start: LocalDateTime,
        private val endInclusive: LocalDateTime,
        private val stepDuration: Duration
    ) : Iterator<LocalDateTime> {

        init {
            require(!stepDuration.isNegative() && stepDuration!=ZERO) { "Step duration must be positive." }
        }

        private var current = start

        override fun hasNext(): Boolean {
            return current <= endInclusive
        }

        override fun next(): LocalDateTime {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            current = current.plus(stepDuration, TimeZone.currentSystemDefault())
            return nextValue
        }
    }
}

// --- 新的 step 擴展函數 ---
infix fun LocalDateTimeRange.step(duration: Duration): LocalDateTimeProgression {
    require(!duration.isNegative() && duration!=ZERO) { "Duration must be positive." }
    return LocalDateTimeProgression(this.start, this.endInclusive, duration)
}

// --- 示例用法 ---
fun main() {
    val start = LocalDateTime(2023, 10, 26, 10, 30, 0)
    val finish = LocalDateTime(2023, 10, 29, 12, 0, 0)
    val formatter = LocalDateTime.Format{
        year()          // 年（2023）
        char('-')       // 分隔符
        monthNumber()   // 月份（10）
        char('-')
        dayOfMonth()    // 日（26）
        char(' ')
        hour()          // 小时（10）
        char(':')
        minute()        // 分钟（30）
        char(':')
        second()        // 秒（0）
    }

    println("--- 按 2 天迭代 ---")
    for (date in start..finish step 2.days) {
        println(date.format(formatter))
    }

    println("\n--- 按 3 小时迭代 ---")
    for (date in start..finish step 3.hours) {
        println(date.format(formatter))
    }

    println("\n--- 按 90 分钟迭代 ---")
    for (date in start..finish step 1.5.hours) {
        println(date.format(formatter))
    }

    println("\n--- 混合时间间隔（2天5小时）---")
    val customDuration = 2.days + 5.hours
    for (date in start..finish step customDuration) {
        println(date.format(formatter))
    }
}