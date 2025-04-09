import kotlinx.datetime.*
import kotlinx.datetime.format.*
import kotlinx.datetime.format.char
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

// --- Helper Extension Functions ---

/**
 * Adds a Duration to a LocalDateTime, considering potential time zone transitions.
 * Requires a TimeZone for correct handling of ambiguities (e.g., DST).
 * Uses the system's default time zone if none is specified.
 */
fun LocalDateTime.plus(duration: Duration, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    val instant = this.toInstant(timeZone)
    val newInstant = instant.plus(duration)
    return newInstant.toLocalDateTime(timeZone)
}
/**
 * Adds a DatePeriod (years, months, days) to a LocalDateTime.
 * Adds the period components to the date part, keeping the time part unchanged.
 */
fun LocalDateTime.plus(period: DatePeriod): LocalDateTime {
    // Add the period to the date component only
    val newDate = this.date.plus(period)
    // Combine the new date with the original time component
    return LocalDateTime(newDate, this.time)
}
/**
 * Adds a DateTimePeriod (date and time components) to a LocalDateTime.
 * Applies component additions sequentially using built-in plus operators.
 */
fun LocalDateTime.plus(period: DateTimePeriod, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    // 1. Extract and add the DatePeriod part
    val datePart = DatePeriod(years = period.years, months = period.months, days = period.days)
    val ldtAfterDatePart = this.plus(datePart) // Uses our existing plus(DatePeriod)

    // 2. Extract and create the Duration part
    val durationPart = period.hours.hours +
            period.minutes.minutes +
            period.seconds.seconds +
            period.nanoseconds.nanoseconds // Use Duration units

    // 3. Add the Duration part using the TimeZone-aware helper
    return ldtAfterDatePart.plus(durationPart, timeZone)
}


/**
 * Subtracts a Duration from a LocalDateTime, considering potential time zone transitions.
 * Requires a TimeZone for correct handling of ambiguities (e.g., DST).
 * Uses the system's default time zone if none is specified.
 */
fun LocalDateTime.minus(duration: Duration, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
    val instant = this.toInstant(timeZone)
    val newInstant = instant.minus(duration)
    return newInstant.toLocalDateTime(timeZone)
}

// --- Utility for DateTimePeriod Validation ---
/**
 * Checks if a DatePeriod represents a positive progression in time.
 * It does this by adding the period to a reference date and checking if the result is later.
 */
internal fun DatePeriod.isPositive(): Boolean {
    if (this == DatePeriod()) return false // Zero period is not positive
    // Use an arbitrary reference date (Epoch date is fine)
    val referenceDate = LocalDate(1970, 1, 1)
    return try {
        // Use LocalDate.plus(DatePeriod) which exists
        referenceDate.plus(this) > referenceDate
    } catch (e: IllegalArgumentException) {
        // Handle potential overflow/underflow if the period is excessively large
        false
    } catch (e: DateTimeArithmeticException) {
        // Handle potential arithmetic issues during addition.
        false
    }
}

internal fun Duration.isPositive(): Boolean {
    return !this.isNegative() && this != ZERO
}

// --- REVISED: DateTimePeriod Positivity Check ---
// Needs to use the TimeZone-aware plus function now
internal fun DateTimePeriod.isPositive(timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    if (this == DateTimePeriod()) return false
    val referenceLDT = LocalDateTime(1970, 1, 1, 0, 0, 0, 0)
    return try {
        // Use the REVISED LocalDateTime.plus(DateTimePeriod, TimeZone)
        referenceLDT.plus(this, timeZone) > referenceLDT
    } catch (e: Exception) {
        false
    }
}



// ============================================
// --- LocalDateTime Range and Progression ---
// ============================================

data class LocalDateTimeRange(
    override val start: LocalDateTime,
    override val endInclusive: LocalDateTime
) : ClosedRange<LocalDateTime>, Iterable<LocalDateTime> {

    override fun iterator(): Iterator<LocalDateTime> {
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

data class LocalDateTimeProgression(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
    val stepDuration: Duration
) : Iterable<LocalDateTime> {

    init {
        require(!stepDuration.isNegative() && stepDuration != ZERO) { "Step duration must be positive and non-zero." }
    }

    override fun iterator(): Iterator<LocalDateTime> {
        // Pass TimeZone.currentSystemDefault() or allow customization if needed
        return LocalDateTimeIterator(start, endInclusive, stepDuration, TimeZone.currentSystemDefault())
    }

    private class LocalDateTimeIterator(
        start: LocalDateTime,
        private val endInclusive: LocalDateTime,
        private val stepDuration: Duration,
        private val timeZone: TimeZone // Pass TimeZone for plus/minus operations
    ) : Iterator<LocalDateTime> {

        private var current = start

        override fun hasNext(): Boolean {
            // Simple check: is the current value still within the inclusive end boundary?
            return current <= endInclusive
        }

        override fun next(): LocalDateTime {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Advance current for the *next* call using the helper extension
            current = current.plus(stepDuration, timeZone)
            return nextValue
        }
    }
}

infix fun LocalDateTimeRange.step(duration: Duration): LocalDateTimeProgression {
    require(!duration.isNegative() && duration != ZERO) { "Step duration must be positive and non-zero." }
    return LocalDateTimeProgression(this.start, this.endInclusive, duration)
}
/**
 * Creates a progression over the [LocalDateTimeRange] with the specified [DatePeriod] step.
 * Adds the period to the date part, keeping the time part unchanged.
 * The period must represent a positive advancement in time.
 */
infix fun LocalDateTimeRange.step(period: DatePeriod): LocalDateTimePeriodProgression {
    require(period.isPositive()) { "Step period must represent a positive time progression." }
    return LocalDateTimePeriodProgression(this.start, this.endInclusive, period)
}
/**
 * Creates a progression over the [LocalDateTimeRange] with the specified [DateTimePeriod] step.
 * Adds both date and time components from the period. Requires a [TimeZone] (uses system default)
 * for correct handling of time components.
 * The period must represent a positive advancement in time.
 */
infix fun LocalDateTimeRange.step(period: DateTimePeriod): LocalDateTimeDateTimePeriodProgression {
    // Uses the revised DateTimePeriod positivity check (implicitly with default TimeZone)
    require(period.isPositive()) { "Step DateTimePeriod must represent a positive time progression." }
    // Creates Progression with default TimeZone. Could allow specifying zone: step(period, zone)
    return LocalDateTimeDateTimePeriodProgression(this.start, this.endInclusive, period)
}


// ======================================
// --- LocalDate Range and Progression ---
// ======================================

data class LocalDateRange(
    override val start: LocalDate,
    override val endInclusive: LocalDate
) : ClosedRange<LocalDate>, Iterable<LocalDate> {

    override fun iterator(): Iterator<LocalDate> {
        // Default step is 1 day using DatePeriod
        return LocalDateProgression(start, endInclusive, DatePeriod(days = 1)).iterator() // Changed here
    }

    override fun contains(value: LocalDate): Boolean {
        return value >= start && value <= endInclusive
    }

    override fun isEmpty(): Boolean = start > endInclusive
}

operator fun LocalDate.rangeTo(that: LocalDate): LocalDateRange {
    return LocalDateRange(this, that)
}

/**
 * Represents an iterable progression over a [LocalDateRange] with a specific [DatePeriod] step.
 * Note: Use DatePeriod (days, months, years) for stepping LocalDate.
 */
data class LocalDateProgression(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val stepPeriod: DatePeriod // Changed here: Use DatePeriod
) : Iterable<LocalDate> {

    init {
        // Use the isPositive helper for DatePeriod
        require(stepPeriod.isPositive()) { "Step period must represent a positive time progression." }
    }

    override fun iterator(): Iterator<LocalDate> {
        return LocalDateIterator(start, endInclusive, stepPeriod)
    }

    // Private iterator implementation
    private class LocalDateIterator(
        start: LocalDate,
        private val endInclusive: LocalDate,
        private val stepPeriod: DatePeriod // Changed here: Use DatePeriod
    ) : Iterator<LocalDate> {

        private var current = start

        override fun hasNext(): Boolean {
            return current <= endInclusive
        }

        override fun next(): LocalDate {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Advance using LocalDate's built-in plus, which takes DatePeriod
            current = current.plus(stepPeriod) // This now correctly matches the available plus method
            return nextValue
        }
    }
}
// --- Progression for Duration Step ---
data class LocalDateTimeDurationProgression(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
    val stepDuration: Duration
) : Iterable<LocalDateTime> {
    init { require(stepDuration.isPositive()) { "Step duration must be positive and non-zero." } }
    override fun iterator(): Iterator<LocalDateTime> =
        LocalDateTimeDurationIterator(start, endInclusive, stepDuration, TimeZone.currentSystemDefault()) // Use default TimeZone

    private class LocalDateTimeDurationIterator(
        start: LocalDateTime,
        private val endInclusive: LocalDateTime,
        private val stepDuration: Duration,
        private val timeZone: TimeZone // TimeZone needed for plus/minus Duration
    ) : Iterator<LocalDateTime> {
        private var current = start
        override fun hasNext(): Boolean = current <= endInclusive
        override fun next(): LocalDateTime {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Use the helper extension requiring TimeZone
            current = current.plus(stepDuration, timeZone)
            return nextValue
        }
    }
}

// --- Progression for DatePeriod Step ---
data class LocalDateTimePeriodProgression(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
    val stepPeriod: DatePeriod
) : Iterable<LocalDateTime> {
    init { require(stepPeriod.isPositive()) { "Step period must represent a positive time progression." } }
    override fun iterator(): Iterator<LocalDateTime> =
        LocalDateTimePeriodIterator(start, endInclusive, stepPeriod)

    private class LocalDateTimePeriodIterator(
        start: LocalDateTime,
        private val endInclusive: LocalDateTime,
        private val stepPeriod: DatePeriod
    ) : Iterator<LocalDateTime> {
        private var current = start
        override fun hasNext(): Boolean = current <= endInclusive
        override fun next(): LocalDateTime {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Use the built-in LocalDateTime.plus(DatePeriod)
            current = current.plus(stepPeriod)
            return nextValue
        }
    }
}

// --- ADJUSTED: Progression for DateTimePeriod Step ---
data class LocalDateTimeDateTimePeriodProgression(
    val start: LocalDateTime,
    val endInclusive: LocalDateTime,
    val stepPeriod: DateTimePeriod,
    // Iterator needs TimeZone, specify default here or require it
    val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : Iterable<LocalDateTime> {
    init {
        // Use the revised DateTimePeriod positivity check
        require(stepPeriod.isPositive(timeZone)) { "Step DateTimePeriod must represent a positive time progression." }
    }
    override fun iterator(): Iterator<LocalDateTime> =
        LocalDateTimeDateTimePeriodIterator(start, endInclusive, stepPeriod, timeZone) // Pass TimeZone

    // --- ADJUSTED: Iterator needs TimeZone ---
    private class LocalDateTimeDateTimePeriodIterator(
        start: LocalDateTime,
        private val endInclusive: LocalDateTime,
        private val stepPeriod: DateTimePeriod,
        private val timeZone: TimeZone // Store TimeZone
    ) : Iterator<LocalDateTime> {
        private var current = start
        override fun hasNext(): Boolean = current <= endInclusive
        override fun next(): LocalDateTime {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Use the REVISED LocalDateTime.plus(DateTimePeriod, TimeZone) extension
            current = current.plus(stepPeriod, timeZone) // Pass TimeZone
            return nextValue
        }
    }
}

/**
 * Creates a [LocalDateProgression] with the specified [DatePeriod] step.
 * The period must represent a positive advancement in time.
 * Example: `(start..end) step DatePeriod(months = 1)`
 * Example: `(start..end) step period { days = 7 }` (using kotlinx.datetime builder for DatePeriod)
 */
infix fun LocalDateRange.step(period: DatePeriod): LocalDateProgression { // Changed here: Use DatePeriod
    // Use the helper extension for validation
    require(period.isPositive()) { "Step period must represent a positive time progression." }
    return LocalDateProgression(this.start, this.endInclusive, period)
}

// ======================================
// --- Instant Range and Progression ---
// ======================================

data class InstantRange(
    override val start: Instant,
    override val endInclusive: Instant
) : ClosedRange<Instant>, Iterable<Instant> {

    override fun iterator(): Iterator<Instant> {
        return InstantProgression(start, endInclusive, 1.days).iterator()
    }

    override fun contains(value: Instant): Boolean {
        return value >= start && value <= endInclusive
    }

    override fun isEmpty(): Boolean = start > endInclusive
}

operator fun Instant.rangeTo(that: Instant): InstantRange {
    return InstantRange(this, that)
}

data class InstantProgression(
    val start: Instant,
    val endInclusive: Instant,
    val stepDuration: Duration
) : Iterable<Instant> {

    init {
        require(!stepDuration.isNegative() && stepDuration != ZERO) { "Step duration must be positive and non-zero." }
    }

    override fun iterator(): Iterator<Instant> {
        return InstantIterator(start, endInclusive, stepDuration)
    }

    private class InstantIterator(
        start: Instant,
        private val endInclusive: Instant,
        private val stepDuration: Duration
    ) : Iterator<Instant> {

        private var current = start

        override fun hasNext(): Boolean {
            return current <= endInclusive
        }

        override fun next(): Instant {
            if (!hasNext()) throw NoSuchElementException()
            val nextValue = current
            // Advance using Instant's built-in plus
            current = current.plus(stepDuration)
            return nextValue
        }
    }
}

infix fun InstantRange.step(duration: Duration): InstantProgression {
    require(!duration.isNegative() && duration != ZERO) { "Step duration must be positive and non-zero." }
    return InstantProgression(this.start, this.endInclusive, duration)
}


// --- Example Usage (Remains the same) ---
fun main() {
    // --- LocalDateTime Examples ---
    println("--- LocalDateTime Examples ---")
    val startLDT = LocalDateTime(2023, 10, 26, 10, 30, 0)
    val finishLDT = LocalDateTime(2023, 10, 29, 12, 0, 0)
    val ldtFormatter = LocalDateTime.Format {
        year(); char('-'); monthNumber(Padding.ZERO); char('-'); dayOfMonth(Padding.ZERO)
        char(' '); hour(Padding.ZERO); char(':'); minute(Padding.ZERO); char(':'); second(Padding.ZERO)
    }

    println("Iterating by default (1 day):")
    for (date in startLDT..finishLDT) {
        println(date.format(ldtFormatter))
    }

    println("\nIterating by 2 days:")
    for (date in startLDT..finishLDT step 2.days) {
        println(date.format(ldtFormatter))
    }

    println("\nIterating by 12 hours:")
    for (date in startLDT..finishLDT step 12.hours) {
        println(date.format(ldtFormatter))
    }

    println("\nIterating by 90 minutes (1.5 hours):")
    for (date in startLDT..finishLDT step 90.minutes) {
        println("${date.format(ldtFormatter)} )")
    }

    println("\nIterating by 1 hour:(use DateTimePeriod)")
    for (date in startLDT..finishLDT step DateTimePeriod(hours = 1)) {
        println(date.format(ldtFormatter))
    }

    println("\nIterating by 1 hour:(use DatePeriod)")
    for (date in startLDT..finishLDT step DatePeriod(days = 1)) {
        println("${date.format(ldtFormatter)})")
    }

    println("\nHandling empty/reversed LocalDateTime range:")
    for (date in finishLDT..startLDT step 1.days) {
        println("This should not appear (LDT): ${date.format(ldtFormatter)}")
    }

    // --- LocalDate Examples ---
    println("\n\n--- LocalDate Examples ---")
    val startDate = LocalDate(2024, 2, 26)
    val endDate = LocalDate(2024, 3, 5) // Leap year, end date inclusive
    val ldFormatter = LocalDate.Format {
        year(); char('-'); monthNumber(Padding.ZERO); char('-'); dayOfMonth(Padding.ZERO)
    }

    println("Iterating by default (1 day):")
    for (date in startDate..endDate) {
        println(date.format(ldFormatter))
    }

    println("\nIterating by 7 days (1 week):")
// Use DatePeriod constructor directly
    for (date in startDate..endDate step DatePeriod(days = 7)) { // Changed here
        println(date.format(ldFormatter))
    }
    // Alternative using DatePeriod builder
    // for (date in startDate..endDate step datePeriod { days = 7 }) { ... } // Note: datePeriod builder

    println("\nIterating by 1 month:") // Note: Month steps jump by calendar month
    val endMonthDate = LocalDate(2024, 5, 15)
    for (date in startDate..endMonthDate step DatePeriod(months = 1)) { // Changed here
        println(date.format(ldFormatter)) // Expect 2024-02-26, 2024-03-26, 2024-04-26
    }

    println("\nIterating by negative period (should fail):")
    try {
        // Use DatePeriod for the invalid step
        val invalidRange = startDate..endDate step DatePeriod(days = -1) // Changed here
        println("This should not be reached.")
        // Force iteration/validation by consuming the iterator (e.g., toList)
        invalidRange.toList() // Or loop like before
    } catch(e: IllegalArgumentException) {
        println("Caught expected error: ${e.message}")
    }


    println("\nHandling empty/reversed LocalDate range:")
    for (date in endDate..startDate step DatePeriod(days=1)) { // Changed here
        println("This should not appear (LD): ${date.format(ldFormatter)}")
    }
    // --- Instant Examples ---
    println("\n\n--- Instant Examples ---")
    // Use Clock for current time or parse ISO strings
    val clock = Clock.System
    val startInstant = Instant.parse("2023-11-10T10:00:00Z")
    val finishInstant = startInstant.plus(3.days).plus(6.hours) // Example end time

    println("Start Instant: $startInstant")
    println("End Instant:   $finishInstant")

    println("\nIterating by default (1 day):")
    for (instant in startInstant..finishInstant) {
        // Instant.toString() is default ISO format
        println(instant)
    }

    println("\nIterating by 6 hours:")
    for (instant in startInstant..finishInstant step 6.hours) {
        println(instant)
    }

    println("\nIterating by 25 hours:")
    for (instant in startInstant..finishInstant step (25.hours)) {
        println(instant)
    }

    println("\nHandling empty/reversed Instant range:")
    for (instant in finishInstant..startInstant step 1.days) {
        println("This should not appear (Instant): $instant")
    }
}