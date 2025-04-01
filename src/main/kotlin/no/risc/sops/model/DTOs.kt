@file:Suppress("ktlint:standard:filename")

package no.risc.sops.model

import no.risc.risc.ProcessingStatus
import java.time.OffsetDateTime

data class GetSopsConfigResponseBody(
    val status: ProcessingStatus,
    val statusMessage: String,
    val sopsConfigs: List<SopsConfigDTO>,
    val gcpCryptoKeys: List<GcpCryptoKeyObject>,
)

data class SopsConfigRequestBody(
    val gcpCryptoKey: GcpCryptoKeyObject,
    val publicAgeKeys: List<PublicAgeKey>,
)

data class SopsConfigDTO(
    val gcpCryptoKey: GcpCryptoKeyObject,
    val publicAgeKeys: List<PublicAgeKey>,
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

data class GcpCryptoKeyObject(
    val projectId: String,
    val keyRing: String,
    val name: String,
    val resourceId: String,
    val hasEncryptDecryptAccess: Boolean,
)
