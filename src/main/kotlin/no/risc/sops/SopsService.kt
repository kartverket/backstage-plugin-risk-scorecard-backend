package no.risc.sops

import no.risc.config.SopsServiceConfig
import no.risc.exception.exceptions.CreateNewBranchException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.github.GithubConnector
import no.risc.google.GoogleServiceIntegration
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.initRiSc.InitRiScServiceIntegration
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.CreateSopsConfigResponseBody
import no.risc.sops.model.GcpCryptoKeyObject
import no.risc.sops.model.OpenPullRequestForSopsConfigResponseBody
import no.risc.sops.model.PublicAgeKey
import no.risc.sops.model.SopsConfigDTO
import no.risc.sops.model.UpdateSopsConfigResponseBody
import no.risc.utils.generateSopsId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SopsService(
    private val githubConnector: GithubConnector,
    private val sopsServiceConfig: SopsServiceConfig,
    private val gcpCloudResourceApiConnector: GcpCloudResourceApiConnector,
    private val initRiScServiceIntegration: InitRiScServiceIntegration,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    private val gcpKmsApiConnector: GcpKmsApiConnector,
    private val googleServiceIntegration: GoogleServiceIntegration,
) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(SopsService::class.java)
    }

    // TODO: Use this when
//                    .mapNotNull { project ->
//                        async(Dispatchers.IO) {
//                            try {
//                                googleServiceIntegration
//                                    .fetchCryptoKeys(
//                                        project,
//                                        accessTokens.gcpAccessToken,
//                                    )?.map { gcpCryptoKey ->
//                                        GcpCryptoKeyObject(
//                                            project.value,
//                                            gcpCryptoKey.getKeyRingName(),
//                                            gcpCryptoKey.getCryptoKeyName(),
//                                            googleServiceIntegration.testIamPermissions(
//                                                gcpCryptoKey.resourceId,
//                                                accessTokens.gcpAccessToken,
//                                                GcpIamPermission.ENCRYPT_DECRYPT,
//                                            ),
//                                        )
//                                    }
//                            } catch (e: WebClientResponseException) {
//                                LOGGER.warn("Received 403 when fetching from ${e.request?.uri}")
//                                null
//                            }
//                        }.await()
//                    }.flatten()

    suspend fun createSopsConfig(
        repositoryOwner: String,
        repositoryName: String,
        gcpCryptoKey: GcpCryptoKeyObject,
        publicAgeKeys: List<PublicAgeKey>,
        accessTokens: AccessTokens,
    ): CreateSopsConfigResponseBody {
        val defaultBranch =
            githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, accessTokens.githubAccessToken.value)
        LOGGER.info("Generating SOPS config for $repositoryOwner/$repositoryName")
        val newSopsConfig = initRiScServiceIntegration.generateSopsConfig(gcpCryptoKey, publicAgeKeys)
        LOGGER.info("Generated SOPS config for $repositoryOwner/$repositoryName")
        val branch = generateSopsId()
        githubConnector.createNewBranch(
            repositoryOwner,
            repositoryName,
            branch,
            accessTokens.githubAccessToken.value,
            defaultBranch,
        ) ?: throw CreateNewBranchException(
            "Unable to create new branch for generate SOPS config",
            response =
                ProcessRiScResultDTO(
                    riScId = "",
                    status = ProcessingStatus.FailedToCreateSops,
                    statusMessage = ProcessingStatus.FailedToCreateSops.message,
                ),
        )

        LOGGER.info("Writing SOPS config github.com/$repositoryOwner/$repositoryName")
        githubConnector.writeSopsConfig(
            newSopsConfig,
            repositoryOwner,
            repositoryName,
            accessTokens.githubAccessToken,
            branch,
        )

        // TODO: This will be the block needed to re-encrypt existing RiSc on default branch with the newly generated SOPS configuration
//        if (reEncryptExistingRiScs) {
//            githubConnector
//                .fetchAllRiScsOnDefaultBranch(
//                    repositoryOwner,
//                    repositoryName,
//                    accessTokens.githubAccessToken,
//                ).map { (file, riSc) ->
//                    file to cryptoServiceIntegration.decrypt(riSc, accessTokens.gcpAccessToken)
//                }.map { (file, decryptedRiSc) ->
//                    file to
//                        cryptoServiceIntegration.encrypt(
//                            decryptedRiSc,
//                            newSopsConfig,
//                            accessTokens.gcpAccessToken,
//                            file.name.split(".$riScPostfix.").first(),
//                        )
//                }.forEach { (file, newlyEncryptedRiSc) ->
//                    githubConnector.putFileRequestToGithub(
//                        repositoryOwner,
//                        repositoryName,
//                        accessTokens.githubAccessToken,
//                        file.path,
//                        branch,
//                        "Re-encrypting RiSc with id: ${file.name.split(
//                            ".$riScPostfix.",
//                        ).first()} with the new SOPS configuration",
//                        newlyEncryptedRiSc.encodeBase64(),
//                    )
//                }
//        }

        LOGGER.info("Wrote SOPS config github.com/$repositoryOwner/$repositoryName")
        return CreateSopsConfigResponseBody(
            ProcessingStatus.CreatedSops,
            ProcessingStatus.CreatedSops.message,
            SopsConfigDTO(
                gcpCryptoKey,
                publicAgeKeys,
            ),
        )
    }

    fun updateSopsConfig(
        repositoryOwner: String,
        repositoryName: String,
        branch: String,
        gcpCryptoKey: GcpCryptoKeyObject,
        publicAgeKeys: List<PublicAgeKey>,
        accessTokens: AccessTokens,
    ): UpdateSopsConfigResponseBody {
        val newSopsConfig = initRiScServiceIntegration.generateSopsConfig(gcpCryptoKey, publicAgeKeys)
        githubConnector.writeSopsConfig(
            newSopsConfig,
            repositoryOwner,
            repositoryName,
            accessTokens.githubAccessToken,
            branch,
        )
        return UpdateSopsConfigResponseBody(
            ProcessingStatus.UpdatedSops,
            ProcessingStatus.UpdatedSops.message,
        )
    }

    fun openPullRequest(
        repositoryOwner: String,
        repositoryName: String,
        sopsId: String,
        githubAccessToken: GithubAccessToken,
    ): OpenPullRequestForSopsConfigResponseBody {
        val defaultBranch = githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, githubAccessToken.value)
        val pullRequestObject =
            githubConnector
                .createPullRequestForSopsConfig(
                    repositoryOwner,
                    repositoryName,
                    sopsId,
                    githubAccessToken,
                    defaultBranch,
                )?.toPullRequestObject() ?: throw GitHubFetchException(
                "Unable to create pull request for sops config with branch: $sopsId",
                ProcessRiScResultDTO(
                    "",
                    ProcessingStatus.ErrorWhenCreatingPullRequest,
                    ProcessingStatus.ErrorWhenCreatingPullRequest.message,
                ),
            )
        return OpenPullRequestForSopsConfigResponseBody(
            ProcessingStatus.OpenedPullRequest,
            ProcessingStatus.OpenedPullRequest.message,
            pullRequestObject,
        )
    }
}
