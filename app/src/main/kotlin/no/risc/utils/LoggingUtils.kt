package no.risc.utils

import no.risc.risc.models.ContentStatus
import no.risc.risc.models.RiScContentResultDTO

fun formatRiScFetchSummary(
    owner: String,
    repository: String,
    results: List<RiScContentResultDTO>,
): String {
    val successful = results.count { it.status == ContentStatus.Success }
    val failed =
        results.filter { it.status != ContentStatus.Success }
    val summary =
        "Fetched ${results.size} RiScs for $owner/$repository " +
            "($successful successful, ${failed.size} failed)"
    if (failed.isEmpty()) return summary
    val details =
        failed
            .groupBy { it.status }
            .entries
            .joinToString(", ") { (status, items) ->
                "$status: ${items.map { it.riScId }}"
            }
    return "$summary — $details"
}
