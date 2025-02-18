package no.risc.sops

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.risc.config.SopsServiceConfig
import no.risc.exception.exceptions.CreateNewBranchException
import no.risc.exception.exceptions.FetchException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.exception.exceptions.NoResourceIdFoundException
import no.risc.github.GithubConnector
import no.risc.google.GoogleServiceIntegration
import no.risc.google.model.GcpIamPermission
import no.risc.google.model.getRiScCryptoKey
import no.risc.google.model.getRiScCryptoKeyResourceId
import no.risc.google.model.getRiScKeyRing
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.initRiSc.InitRiScServiceIntegration
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.CreateSopsConfigResponseBody
import no.risc.sops.model.GcpCryptoKeyObject
import no.risc.sops.model.GetSopsConfigResponseBody
import no.risc.sops.model.OpenPullRequestForSopsConfigResponseBody
import no.risc.sops.model.PublicAgeKey
import no.risc.sops.model.SopsConfig
import no.risc.sops.model.SopsConfigDTO
import no.risc.sops.model.UpdateSopsConfigResponseBody
import no.risc.sops.model.getRiScCryptoKey
import no.risc.sops.model.getRiScCryptoKeyResourceId
import no.risc.sops.model.getRiScKeyRing
import no.risc.utils.YamlUtils
import no.risc.utils.generateSopsId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

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

    suspend fun getSopsConfigs(
        repositoryOwner: String,
        repositoryName: String,
        accessTokens: AccessTokens,
    ): GetSopsConfigResponseBody =
        coroutineScope {
            LOGGER.info("Fetching SOPS config for $repositoryOwner/$repositoryName")
            val defaultBranch =
                githubConnector.fetchDefaultBranch(
                    repositoryOwner,
                    repositoryName,
                    accessTokens.githubAccessToken.value,
                )

            val sopsBranches =
                githubConnector
                    .fetchAllBranches(repositoryOwner, repositoryName, accessTokens.githubAccessToken)
                    ?.filter { it.name.startsWith("sops-") }
                    ?.map { it.name } ?: throw FetchException(
                    "Unable to fetch sops branches for $repositoryOwner/$repositoryName",
                    ProcessingStatus.FailedToFetchGcpProjectIds,
                )

            val sopsBranchesToSopsConfigs =
                sopsBranches
                    .associateWith { branch ->
                        async(Dispatchers.IO) {
                            try {
                                githubConnector
                                    .fetchSopsConfig(
                                        repositoryOwner,
                                        repositoryName,
                                        accessTokens.githubAccessToken,
                                        branch,
                                    ).data
                            } catch (e: WebClientResponseException.NotFound) {
                                null
                            }
                        }
                    }.map {
                        it.key to
                            it.value.await()?.let { sopsConfigAsString ->
                                YamlUtils.deSerialize<SopsConfig>(sopsConfigAsString)
                            }
                    }.toMutableList()
            sopsBranchesToSopsConfigs.add(
                defaultBranch to
                    async(Dispatchers.IO) {
                        try {
                            val sopsConfigAsString =
                                githubConnector
                                    .fetchSopsConfig(
                                        repositoryOwner,
                                        repositoryName,
                                        accessTokens.githubAccessToken,
                                        defaultBranch,
                                    ).data
                            sopsConfigAsString?.let { YamlUtils.deSerialize<SopsConfig>(it) }
                        } catch (e: WebClientResponseException.NotFound) {
                            null
                        }
                    }.await(),
            )
            val pullRequests =
                githubConnector
                    .fetchPullRequestsForBranches(
                        repositoryOwner,
                        repositoryName,
                        accessTokens.githubAccessToken,
                        defaultBranch,
                        sopsBranches,
                    ).associateWith {
                        async(Dispatchers.IO) {
                            githubConnector.fetchFilesUpdatedInPullRequest(
                                repositoryOwner,
                                repositoryName,
                                accessTokens.githubAccessToken,
                                it,
                            )
                        }
                    }.filter { (_, files) ->
                        files.await().any { it.filename == "$riScFolderPath/.sops.yaml" }
                    }.keys

            val gcpProjectIds =
                googleServiceIntegration.fetchProjectIds(accessTokens.gcpAccessToken)
                    ?: throw FetchException(
                        "Failed to fetch GCP projects",
                        ProcessingStatus.FailedToFetchGcpProjectIds,
                    )
            val cryptoKeys =
                gcpProjectIds
                    .filter { it.value.contains("-prod-") }
                    .map {
                        it to
                            async(Dispatchers.IO) {
                                googleServiceIntegration.testIamPermissions(
                                    it.getRiScCryptoKeyResourceId(),
                                    accessTokens.gcpAccessToken,
                                    GcpIamPermission.ENCRYPT_DECRYPT,
                                )
                            }
                    }.map { (gcpProjectId, hasAccess) ->
                        GcpCryptoKeyObject(
                            gcpProjectId.value,
                            gcpProjectId.getRiScKeyRing(),
                            gcpProjectId.getRiScCryptoKey(),
                            gcpProjectId.getRiScCryptoKeyResourceId(),
                            hasAccess.await(),
                        )
                    }.toMutableList()

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
            GetSopsConfigResponseBody(
                status = ProcessingStatus.FetchedSopsConfig,
                statusMessage = ProcessingStatus.FetchedSopsConfig.message,
                sopsConfigs =
                    sopsBranchesToSopsConfigs.mapNotNull
                        { (sopsBranch, sopsConfig) ->
                            if (sopsConfig != null) {
                                val gcpCryptoKey = getGcpCryptoKey(sopsConfig, accessTokens.gcpAccessToken)
                                if (gcpCryptoKey !in cryptoKeys) {
                                    cryptoKeys.add(gcpCryptoKey)
                                }
                                SopsConfigDTO(
                                    gcpCryptoKey,
                                    sopsConfig.getDeveloperPublicKeys(sopsServiceConfig.backendPublicKey)
                                )
                            } else {
                                null
                            }
                        },
                gcpCryptoKeys = cryptoKeys,
            )
        }

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

    private fun getGcpCryptoKey(
        sopsConfig: SopsConfig,
        gcpAccessToken: GCPAccessToken,
    ): GcpCryptoKeyObject {
        val resourceId =
            sopsConfig.key_groups
                .firstOrNull { !it.gcp_kms.isNullOrEmpty() }
                ?.gcp_kms
                ?.firstOrNull()
                ?.resourceId
                ?: throw NoResourceIdFoundException(
                    "No gcp kms resource id could be found",
                    ProcessRiScResultDTO(
                        "",
                        ProcessingStatus.NoGcpKeyInSopsConfigFound,
                        ProcessingStatus.NoGcpKeyInSopsConfigFound.message,
                    ),
                )
        return GcpCryptoKeyObject(
            resourceId.split("/")[1],
            resourceId.split("/")[5],
            resourceId.split("/").last(),
            resourceId,
            googleServiceIntegration.testIamPermissions(
                resourceId,
                gcpAccessToken,
                GcpIamPermission.ENCRYPT_DECRYPT,
            ),
        )
    }
}
