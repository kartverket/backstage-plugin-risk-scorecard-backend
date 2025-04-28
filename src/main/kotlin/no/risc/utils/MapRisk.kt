package no.risc.utils

// lag en funksjon  som beregner risikio basert på 20 i grunntall, tar inn konsekvens og sannsynlighet
// K = 2 og S = 3 mater det inn formelen med 20 i grunntall, og da får man ut R. Hvis dette IKKE matcher R = hvis man har brukt har 1-5, så finn nærmeste.

// hva skjer npr man poster, endrer eller lager ny? skal man da bare sende ned tallet? I så fall må man mappe til faktiske verdien i backenden.

import kotlin.math.ln
import kotlin.math.roundToInt

data class RiScValues(
    val probabilityIndex: Int,
    val consequenceIndex: Int,
)

const val BASE_NUMBER: Double = 20.0

fun logBase(
    value: Double,
    base: Double,
): Double = ln(value) / ln(base)

fun findIndexes(
    probability: Double,
    consequence: Int,
): RiScValues {
    val probabilityIndex = (logBase(probability, BASE_NUMBER) + 3 - 1).roundToInt().coerceIn(0, 4) // min/max (0-4)
    val consequenceIndex = (logBase(consequence.toDouble(), BASE_NUMBER) - 2 - 1).roundToInt().coerceIn(0, 4) // min/max (0-4)

    return RiScValues(probabilityIndex, consequenceIndex)
}
