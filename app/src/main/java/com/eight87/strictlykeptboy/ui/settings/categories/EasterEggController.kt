package com.eight87.strictlykeptboy.ui.settings.categories

/**
 * Tonearmboy-parity easter-egg state machine. Three taps inside a five-
 * second window reveals the mascot; the first two taps surface a
 * snackbar nudge so the user knows something is brewing. Taps outside
 * the window reset the counter so the egg can be replayed.
 *
 * Framework-free so it can be unit-tested with a synthetic clock — the
 * Compose call site passes `System.currentTimeMillis()` at tap time.
 */
class EasterEggController(
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    sealed class Outcome {
        data object FirstPromptSnackbar : Outcome()
        data object SecondPromptSnackbar : Outcome()
        data object Reveal : Outcome()
    }

    private var counter: Int = 0
    private var lastTapAtMillis: Long = 0L

    fun tap(nowMillis: Long): Outcome {
        if (counter > 0 && (nowMillis - lastTapAtMillis) > windowMillis) {
            counter = 0
        }
        counter += 1
        lastTapAtMillis = nowMillis
        return when (counter) {
            1 -> Outcome.FirstPromptSnackbar
            2 -> Outcome.SecondPromptSnackbar
            else -> {
                counter = 0
                Outcome.Reveal
            }
        }
    }

    internal fun debugCount(): Int = counter

    companion object {
        const val DEFAULT_WINDOW_MILLIS: Long = 5_000L
    }
}
