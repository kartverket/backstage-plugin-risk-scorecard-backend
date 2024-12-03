@file:Suppress("ktlint:standard:filename")

package no.risc.sops.model

import no.risc.risc.ProcessingStatus
import java.time.OffsetDateTime

data class GetSopsConfigResponseBody(
    val status: ProcessingStatus,
    val statusMessage: String,
    val sopsConfigs: List<SopsConfigDTO>,
    val gcpProjectIds: List<GcpProjectId>,
)

data class SopsConfigRequestBody(
    val gcpProjectId: GcpProjectId,
    val publicAgeKeys: List<PublicAgeKey>,
)

data class SopsConfigDTO(
    val gcpProjectId: GcpProjectId,
    val publicAgeKeys: List<PublicAgeKey>,
    val onDefaultBranch: Boolean,
    val branch: String,
    val pullRequest: PullRequestObject?,
)

data class CreateSopsConfigResponseBody(
    val status: ProcessingStatus,
    val statusMessage: String,
    val sopsConfig: SopsConfigDTO,
)

data class UpdateSopsConfigResponseBody(
    val status: ProcessingStatus,
    val statusMessage: String,
)

data class OpenPullRequestForSopsConfigResponseBody(
    val status: ProcessingStatus,
    val statusMessage: String,
    val pullRequest: PullRequestObject,
)

data class PullRequestObject(
    val url: String,
    val title: String,
    val openedBy: String,
    val createdAt: OffsetDateTime,
)
