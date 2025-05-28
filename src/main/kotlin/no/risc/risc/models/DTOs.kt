package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.risc.utils.Difference
import no.risc.utils.KOffsetDateTimeSerializer
import java.time.OffsetDateTime

@Serializable
data class DifferenceDTO(
    val status: DifferenceStatus,
    val differenceState: Difference,
    val errorMessage: String = "",
    val defaultLastModifiedDateString: String = "",
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class DifferenceRequestBody(
    val riSc: String,
)

@Serializable
data class DecryptionFailureDTO(
    val status: ContentStatus,
    val message: String,
)

@Serializable
data class CreateRiScResultDTO(
    val riScId: String,
    val status: ProcessingStatus,
    val statusMessage: String,
    val riScContent: String?,
    val sopsConfig: SopsConfig,
)

@Serializable
data class LastPublished(
    @Serializable(KOffsetDateTimeSerializer::class)
    val dateTime: OffsetDateTime,
    val numberOfCommits: Int,
)

@Serializable
data class RiScContentResultDTO(
    val riScId: String,
    val status: ContentStatus,
    val riScStatus: RiScStatus?,
    val riScContent: String?,
    val lastPublished: LastPublished? = null,
    val sopsConfig: SopsConfig? = null,
    val pullRequestUrl: String? = null,
    val migrationStatus: MigrationStatus =
        MigrationStatus(
            migrationChanges = false,
            migrationRequiresNewApproval = false,
            migrationVersions =
                MigrationVersions(
                    fromVersion = null,
                    toVersion = null,
                ),
        ),
)

@Serializable
data class MigrationStatus(
    val migrationChanges: Boolean,
    val migrationRequiresNewApproval: Boolean,
    val migrationVersions: MigrationVersions,
)

@Serializable
data class MigrationVersions(
    var fromVersion: String?,
    var toVersion: String?,
)

enum class ContentStatus {
    Success,
    FileNotFound,
    DecryptionFailed,
    Failure,
    NoReadAccess,
    SchemaNotFound,
    SchemaValidationFailed,
}

enum class DifferenceStatus {
    Success,
    GithubFailure,
    GithubFileNotFound,
    JsonFailure,
    DecryptionFailure,
    NoReadAccess,
    SchemaNotFound,
    SchemaValidationFailed,
}

@Serializable
abstract class RiScResult {
    abstract val riScId: String
    abstract val status: ProcessingStatus
    abstract val statusMessage: String
}

@Serializable
class ProcessRiScResultDTO(
    override val riScId: String,
    override val status: ProcessingStatus,
    override val statusMessage: String,
) : RiScResult() {
    companion object {
        val INVALID_ACCESS_TOKENS =
            ProcessRiScResultDTO(
                "",
                ProcessingStatus.InvalidAccessTokens,
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
    }
}

@Serializable
class DeleteRiScResultDTO(
    override val riScId: String,
    override val status: ProcessingStatus,
    override val statusMessage: String,
) : RiScResult()

@Serializable
class PublishRiScResultDTO(
    override val riScId: String,
    override val status: ProcessingStatus,
    override val statusMessage: String,
    val pendingApproval: PendingApprovalDTO?,
) : RiScResult()

@Serializable
data class PendingApprovalDTO(
    val pullRequestUrl: String,
    val pullRequestName: String,
)

enum class ProcessingStatus(
    val message: String,
) {
    ErrorWhenUpdatingRiSc("Error when updating risk scorecard"),
    CreatedRiSc("Created new risk scorecard successfully"),
    UpdatedRiSc("Updated risk scorecard successfully"),
    DeletedRiSc("Deleted risk scorecard successfully"),
    DeletedRiScRequiresApproval("Deleted risk scorecard and requires approval"),
    ErrorWhenDeletingRiSc("Error when deleting risk scorecard"),
    UpdatedRiScAndCreatedPullRequest("Updated risk scorecard and created pull request"),
    CreatedPullRequest("Created pull request for risk scorecard"),
    ErrorWhenCreatingPullRequest("Error when creating pull request"),
    InvalidAccessTokens("Invalid access tokens"),
    NoWriteAccessToRepository("Permission denied: You do not have write access to repository"),
    UpdatedRiScRequiresNewApproval("Updated risk scorecard and requires new approval"),
    ErrorWhenCreatingRiSc("Error when creating risk scorecard"),
    AccessTokensValidationFailure("Failure when validating access tokens"),
    FailedToFetchGcpProjectIds("Failed to fetch GCP project IDs"),
    FailedToFetchGCPOAuth2TokenInformation("Failed to fetch GCP OAuth2 token information"),
    FailedToFetchGCPIAMPermissions("Failed to fetch GCP IAM permissions for crypto key"),
    FailedToCreateSops("Failed to create SOPS configuration"),
}

@Serializable
data class RiScIdentifier(
    val id: String,
    var status: RiScStatus,
    val pullRequestUrl: String? = null,
)

enum class RiScStatus {
    Draft,
    SentForApproval,
    Published,
    DeletionDraft,
    DeletionSentForApproval,
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class UserInfo(
    val name: String,
    val email: String,
) {
    override fun toString(): String = "$name ($email)"
}
