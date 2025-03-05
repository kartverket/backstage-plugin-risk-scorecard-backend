package no.risc.risc.models

import no.risc.sops.model.SopsConfig

data class RiScWithConfig(
    val riSc: String,
    val sopsConfig: SopsConfig,
)
