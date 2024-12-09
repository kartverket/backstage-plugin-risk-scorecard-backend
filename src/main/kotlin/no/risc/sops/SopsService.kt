package no.risc.sops

import no.risc.config.SopsServiceConfig
import no.risc.encryption.CryptoServiceIntegration
import no.risc.exception.exceptions.CreateNewBranchException
import no.risc.exception.exceptions.GcpProjectIdFetchException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.github.GithubConnector
import no.risc.github.GithubHelper
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.initRiSc.InitRiScServiceIntegration
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.CreateSopsConfigResponseBody
import no.risc.sops.model.GcpProjectId
import no.risc.sops.model.GetSopsConfigResponseBody
import no.risc.sops.model.OpenPullRequestForSopsConfigResponseBody
import no.risc.sops.model.PublicAgeKey
import no.risc.sops.model.SopsConfig
import no.risc.sops.model.SopsConfigDTO
import no.risc.sops.model.UpdateSopsConfigResponseBody
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
    private val googleApiConnector: GoogleApiConnector,
    private val initRiScServiceIntegration: InitRiScServiceIntegration,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    @Value("\${filename.postfix}") private val riScPostfix: String,
    @Value("\${filename.prefix}") private val riScPrefix: String,
    private val cryptoServiceIntegration: CryptoServiceIntegration,
    private val githubHelper: GithubHelper,
) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(SopsService::class.java)
    }

    fun getSopsConfigs(
        repositoryOwner: String,
        repositoryName: String,
        accessTokens: AccessTokens,
    ): GetSopsConfigResponseBody {
        LOGGER.info("Fetching SOPS config for $repositoryOwner/$repositoryName")

        val sopsBranches =
            githubConnector
                .fetchAllBranches(repositoryOwner, repositoryName, accessTokens.githubAccessToken)
                ?.filter { it.name.startsWith("sops-") }
                ?.map { it.name } ?: throw GitHubFetchException(
                "Error occured when parsing GitHub-response when fetching repository branches",
                ProcessRiScResultDTO(
                    "",
                    ProcessingStatus.FailedToCreateSops,
                    ProcessingStatus.FailedToCreateSops.message,
                ),
            )

        val sopsBranchesToSopsConfigs =
            sopsBranches
                .associateWith {
                    try {
                        githubConnector
                            .fetchSopsConfig(
                                repositoryOwner,
                                repositoryName,
                                accessTokens.githubAccessToken,
                                it,
                            ).data
                    } catch (e: WebClientResponseException.NotFound) {
                        null
                    }
                }.map { (branch, sopsConfigAsString) ->
                    branch to sopsConfigAsString?.let { YamlUtils.deSerialize<SopsConfig>(sopsConfigAsString) }
                }.toMutableList()

        val defaultBranch =
            githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, accessTokens.githubAccessToken.value)
        sopsBranchesToSopsConfigs.add(
            Pair(
                defaultBranch,
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
                },
            ),
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
                    githubConnector.fetchFilesUpdatedInPullRequest(
                        repositoryOwner,
                        repositoryName,
                        accessTokens.githubAccessToken,
                        it,
                    )
                }.filter { (_, files) ->
                    files.any { it.filename == "$riScFolderPath/.sops.yaml" }
                }.keys

        val gcpProjectIds =
            googleApiConnector.fetchProjectIds(accessTokens.gcpAccessToken)
                ?: throw GcpProjectIdFetchException(
                    message = "Fetch of sops config responded with 200 OK but file contents was null",
                    ProcessRiScResultDTO(
                        "",
                        ProcessingStatus.FailedToFetchGcpProjectIds,
                        ProcessingStatus.FailedToFetchGcpProjectIds.message,
                    ),
                )

        val response =
            GetSopsConfigResponseBody(
                status = ProcessingStatus.FetchedSopsConfig,
                statusMessage = ProcessingStatus.FetchedSopsConfig.message,
                gcpProjectIds = gcpProjectIds.filter { it.value.contains("-prod-") },
                sopsConfigs =
                    sopsBranchesToSopsConfigs
                        .map { (sopsBranch, sopsConfig) ->
                            if (sopsConfig != null) {
                                SopsConfigDTO(
                                    sopsConfig.getGcpProjectId(),
                                    sopsConfig.getDeveloperPublicKeys(sopsServiceConfig.backendPublicKey),
                                    sopsBranch == defaultBranch,
                                    sopsBranch,
                                    pullRequests.firstOrNull { it.head.ref == sopsBranch }?.toPullRequestObject(),
                                )
                            } else {
                                null
                            }
                        }.filterNotNull(),
            )
        return response
    }

    suspend fun createSopsConfig(
        repositoryOwner: String,
        repositoryName: String,
        gcpProjectId: GcpProjectId,
        publicAgeKeys: List<PublicAgeKey>,
        accessTokens: AccessTokens,
    ): CreateSopsConfigResponseBody {
        val defaultBranch =
            githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, accessTokens.githubAccessToken.value)
        val newSopsConfig = initRiScServiceIntegration.generateSopsConfig(gcpProjectId, publicAgeKeys)
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

        return CreateSopsConfigResponseBody(
            ProcessingStatus.CreatedSops,
            ProcessingStatus.CreatedSops.message,
            SopsConfigDTO(
                gcpProjectId,
                publicAgeKeys,
                false,
                branch,
                null,
            ),
        )
    }

    fun updateSopsConfig(
        repositoryOwner: String,
        repositoryName: String,
        branch: String,
        gcpProjectId: GcpProjectId,
        publicAgeKeys: List<PublicAgeKey>,
        accessTokens: AccessTokens,
    ): UpdateSopsConfigResponseBody {
        val newSopsConfig = initRiScServiceIntegration.generateSopsConfig(gcpProjectId, publicAgeKeys)
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
