package no.risc.github

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.exception.exceptions.SopsConfigFetchException
import no.risc.exception.exceptions.UnableToWriteSopsConfigException
import no.risc.github.models.FileContentDTO
import no.risc.github.models.FileContentsDTO
import no.risc.github.models.FileNameDTO
import no.risc.github.models.RepositoryDTO
import no.risc.github.models.ShaResponseDTO
import no.risc.infra.connector.WebClientConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GitHubPermission
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.infra.connector.models.RepositoryInfo
import no.risc.risc.LastPublished
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.risc.models.UserInfo
import no.risc.utils.decodeBase64
import no.risc.utils.encodeBase64
import no.risc.utils.tryOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.util.Date

data class GithubContentResponse(
    val data: String?,
    val status: GithubStatus,
) {
    fun data(): String = data!!
}

data class GithubRiScIdentifiersResponse(
    val ids: List<RiScIdentifier>,
    val status: GithubStatus,
)

enum class GithubStatus {
    NotFound,
    Unauthorized,
    ContentIsEmpty,
    Success,
    RequestResponseBodyError,
    ResponseBodyTooLargeForWebClientError,
    InternalError,
}

data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branchName: String,
    val author: Author? = null,
) {
    fun toContentBody(): String =
        "{\"message\": \"$message\", \"content\": \"$content\", \"branch\": \"$branchName\"" +
            (author?.let { ", \"committer\": ${author.toJSONString()}" } ?: "") +
            (sha?.let { ", \"sha\": \"$sha\"" } ?: "") +
            "}"
}

data class Author(
    val name: String?,
    val email: String?,
    val date: Date,
) {
    private fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)

    fun toJSONString(): String = "{ \"name\":\"${name}\", \"email\":\"${email}\", \"date\":\"${formattedDate()}\" }"
}

data class RiScApprovalPRStatus(
    val pullRequest: GithubPullRequestObject?,
    val hasClosedPr: Boolean,
)

@Component
class GithubConnector(
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    private val githubHelper: GithubHelper,
) : WebClientConnector("https://api.github.com/repos") {
    companion object {
        val LOGGER = LoggerFactory.getLogger(GithubConnector::class.java)
    }

    suspend fun fetchSopsConfig(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
        branch: String,
    ): GithubContentResponse {
        LOGGER.info("Trying to get sops config from branch: $branch")
        val sopsConfig =
            try {
                LOGGER.info("Trying to get sops config from branch: $branch")
                getGithubResponseSuspend(
                    uri = "${githubHelper.uriToFindSopsConfig(owner, repository)}?ref=$branch",
                    accessToken = githubAccessToken.value,
                ).toFileContentDTO()?.content?.decodeBase64()
            } catch (e: WebClientResponseException.NotFound) {
                LOGGER.info("Trying to get sops config from default branch")
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFindSopsConfig(owner, repository),
                    accessToken = githubAccessToken.value,
                ).toFileContentDTO()?.content?.decodeBase64()
            }
        if (sopsConfig == null) {
            throw SopsConfigFetchException(
                message = "Fetch of sops config responded with 200 OK but file contents was null",
                riScId = branch,
                responseMessage = "Could not fetch SOPS config",
            )
        }

        return GithubContentResponse(data = sopsConfig, status = GithubStatus.Success)
    }

    fun fetchSopsConfigFromDefaultBranch(
        repositoryOwner: String,
        repositoryName: String,
        githubAccessToken: GithubAccessToken,
    ): GithubContentResponse {
        val sopsConfig =
            getGithubResponse(
                uri = githubHelper.uriToFindSopsConfig(owner = repositoryOwner, repository = repositoryName),
                accessToken = githubAccessToken.value,
            ).bodyToMono<FileContentDTO>()
                .block()
                ?.content
                ?.decodeBase64()

        if (sopsConfig == null) {
            throw SopsConfigFetchException(
                message = "Fetch of sops config responded with 200 OK but file contents was null",
                riScId = "",
                responseMessage = "Could not fetch SOPS config",
            )
        }
        return GithubContentResponse(data = sopsConfig, status = GithubStatus.Success)
    }

    suspend fun fetchAllRiScIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRiScIdentifiersResponse =
        coroutineScope {
            val draftRiScsDeferred =
                async {
                    fetchRiScIdentifiersDrafted(
                        owner = owner,
                        repository = repository,
                        accessToken = accessToken,
                    )
                }
            val publishedRiScsDeferred =
                async {
                    fetchPublishedRiScIdentifiers(
                        owner = owner,
                        repository = repository,
                        accessToken = accessToken,
                    )
                }
            val riScsSentForApprovalDeferred =
                async {
                    fetchRiScIdentifiersSentForApproval(
                        owner = owner,
                        repository = repository,
                        accessToken = accessToken,
                    )
                }

            val draftRiScs = draftRiScsDeferred.await()
            val publishedRiScs = publishedRiScsDeferred.await()
            val riScsSentForApproval = riScsSentForApprovalDeferred.await()

            GithubRiScIdentifiersResponse(
                status = GithubStatus.Success,
                ids =
                    combinePublishedDraftAndSentForApproval(
                        draftRiScList = draftRiScs,
                        sentForApprovalList = riScsSentForApproval,
                        publishedRiScList = publishedRiScs,
                    ),
            )
        }

    private fun combinePublishedDraftAndSentForApproval(
        draftRiScList: List<RiScIdentifier>,
        sentForApprovalList: List<RiScIdentifier>,
        publishedRiScList: List<RiScIdentifier>,
    ): List<RiScIdentifier> {
        val sentForApprovalsIds = sentForApprovalList.map { it.id }.toSet()

        val combinedList = mutableListOf<RiScIdentifier>()
        combinedList.addAll(sentForApprovalList)

        for (published in publishedRiScList) {
            if (published.id !in sentForApprovalsIds) {
                combinedList.add(published)
            }
        }

        for (draft in draftRiScList) {
            if (draft.id !in sentForApprovalsIds) {
                combinedList.add(draft)
            }
        }

        return combinedList
    }

    suspend fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFindRiSc(owner = owner, repository = repository, id = id),
                    accessToken = accessToken,
                ).decodedFileContentSuspend()

            GithubContentResponse(
                data = fileContent,
                status = if (fileContent == null) GithubStatus.ContentIsEmpty else GithubStatus.Success,
            )
        } catch (e: Exception) {
            GithubContentResponse(data = null, status = mapWebClientExceptionToGithubStatus(e))
        }

    suspend fun fetchDraftedRiScContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFindRiScOnDraftBranch(owner = owner, repository = repository, riScId = id),
                    accessToken = accessToken,
                ).decodedFileContentSuspend()
            GithubContentResponse(
                data = fileContent,
                status = if (fileContent == null) GithubStatus.ContentIsEmpty else GithubStatus.Success,
            )
        } catch (e: Exception) {
            GithubContentResponse(data = null, status = mapWebClientExceptionToGithubStatus(e))
        }

    private suspend fun fetchPublishedRiScIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            val response =
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFindRiScFiles(owner, repository),
                    accessToken = accessToken,
                ).awaitBody<List<FileNameDTO>>()

            response.riScIdentifiersPublished()
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun fetchRiScIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            val response =
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFetchAllPullRequests(owner = owner, repository = repository),
                    accessToken = accessToken,
                ).awaitBody<List<GithubPullRequestObject>>()

            response.riScIdentifiersSentForApproval()
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun fetchRiScIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            val response =
                getGithubResponseSuspend(
                    uri = githubHelper.uriToFindAllRiScBranches(owner = owner, repository = repository),
                    accessToken = accessToken,
                ).awaitBody<List<GithubReferenceObjectDTO>>().map { it.toInternal() }

            response.riScIdentifiersDrafted()
        } catch (e: Exception) {
            emptyList()
        }

    internal suspend fun fetchLastPublishedRiScDateAndCommitNumber(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
    ): LastPublished? =
        tryOrNull {
            val lastCommitOnPath =
                getGithubResponseSuspend(
                    githubHelper.uriToFetchCommits(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                    ),
                    accessToken,
                ).awaitBody<List<GithubRefCommitDTO>>()[0]

            val dateOfLastPublished = lastCommitOnPath.commit.committer.dateTime

            val numberOfCommitsSinceDateTime =
                getGithubResponseSuspend(
                    githubHelper.uriToFetchCommitsSince(
                        owner = owner,
                        repository = repository,
                        since = dateOfLastPublished,
                    ),
                    accessToken,
                ).awaitBody<List<GithubRefCommitDTO>>().size

            LastPublished(dateOfLastPublished, numberOfCommitsSinceDateTime)
        }

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        riScId: String,
        defaultBranch: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
    ): RiScApprovalPRStatus {
        val accessToken = accessTokens.githubAccessToken.value
        // Attempt to get SHA for the existing draft
        val latestShaForDraft =
            getSHAForExistingRiScDraftOrNull(
                owner = owner,
                repository = repository,
                riScId = riScId,
                accessToken = accessToken,
            )
        var latestShaForPublished: String? = null

        // A new branch is needed if an existing branch was not found
        if (latestShaForDraft == null) {
            runBlocking {
                val newBranchDeferred =
                    async {
                        createNewBranch(
                            owner = owner,
                            repository = repository,
                            newBranchName = riScId,
                            accessToken = accessToken,
                            defaultBranch = defaultBranch,
                        )
                    }

                // Determine if the change is an update or create request
                latestShaForPublished =
                    async {
                        getSHAForPublishedRiScOrNull(
                            owner = owner,
                            repository = repository,
                            riScId = riScId,
                            accessToken = accessToken,
                        )
                    }.await()

                newBranchDeferred.await()
            }
        }

        // "requires new approval" is used to determine if new PR can be created through updating.
        val commitMessage =
            if (latestShaForDraft == null && latestShaForPublished == null) {
                "Create new RiSc with id: $riScId" + if (requiresNewApproval) " requires new approval" else ""
            } else {
                "Update RiSc with id: $riScId" + if (requiresNewApproval) " requires new approval" else ""
            }

        putFileRequestToGithub(
            repositoryOwner = owner,
            repositoryName = repository,
            gitHubAccessToken = accessTokens.githubAccessToken,
            filePath = "$riScFolderPath/$riScId.$filenamePostfix.yaml",
            branch = riScId,
            message = commitMessage,
            content = fileContent.encodeBase64(),
        ).bodyToMono<String>().block()

        val riScApprovalPRStatus =
            runBlocking {
                val prExistsDeferred =
                    async {
                        pullRequestForRiScExists(
                            owner = owner,
                            repository = repository,
                            riScId = riScId,
                            accessToken = accessToken,
                        )
                    }

                val commitsAheadOfDefaultRequiresApprovalDeferred =
                    async {
                        // Latest commit timestamp on default branch that includes changes on this riSc
                        val latestCommitTimestamp =
                            fetchLatestCommitTimestampOnDefault(
                                owner = owner,
                                repository = repository,
                                accessToken = accessToken,
                                riScId = riScId,
                                branch = defaultBranch,
                            )

                        // Check if previous commits on draft branch ahead of default branch requires approval.
                        latestCommitTimestamp
                            ?.let { it ->
                                fetchCommitsSinceLastCommit(
                                    owner = owner,
                                    repository = repository,
                                    accessToken = accessToken,
                                    riScId = riScId,
                                    since = it,
                                ).filterNot { it.commit.committer.date == latestCommitTimestamp }
                            }?.any { it.commit.message.contains("requires new approval") } ?: true
                    }

                val prExists = prExistsDeferred.await()
                val commitsAheadOfDefaultRequiresApproval = commitsAheadOfDefaultRequiresApprovalDeferred.await()

                // If no PR already exists and the update does not require new approval, as the usual case
                // is that approving the RiSc from the plugin triggers the creation of a new PR, and no commits ahead
                // of default requires approval, then create a new PR.

                // else if a pull request already exists (meaning the RiSc has been approved)
                // close it if the update requires new approval
                if (!requiresNewApproval && !prExists && !commitsAheadOfDefaultRequiresApproval) {
                    val pullRequest =
                        createPullRequestForRiSc(
                            owner = owner,
                            repository = repository,
                            riScId = riScId,
                            requiresNewApproval = requiresNewApproval,
                            accessTokens = accessTokens,
                            userInfo = userInfo,
                            baseBranch = defaultBranch,
                        )
                    RiScApprovalPRStatus(pullRequest = pullRequest, hasClosedPr = false)
                } else if (requiresNewApproval && prExists) {
                    closePullRequestForRiSc(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                        accessToken = accessToken,
                    )
                    RiScApprovalPRStatus(pullRequest = null, hasClosedPr = true)
                } else {
                    RiScApprovalPRStatus(pullRequest = null, hasClosedPr = false)
                }
            }
        return riScApprovalPRStatus
    }

    private fun closePullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? =
        fetchAllPullRequests(owner, repository, accessToken).find { it.head.ref == riScId }?.let {
            try {
                closePullRequest(
                    uri =
                        githubHelper.uriToEditPullRequest(
                            owner = owner,
                            repository = repository,
                            pullRequestNumber = it.number,
                        ),
                    accessToken = accessToken,
                    closePullRequestBody = githubHelper.bodyToClosePullRequest(),
                ).bodyToMono<String>().block()
            } catch (e: Exception) {
                LOGGER.error("Could not close pull request with error message: ${e.message}.")
                null
            }
        }

    private fun getSHAForExistingRiScDraftOrNull(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ) = tryOrNull {
        getGithubResponse(
            uri = githubHelper.uriToFindRiScOnDraftBranch(owner = owner, repository = repository, riScId = riScId),
            accessToken = accessToken,
        ).shaResponseDTO()
    }

    private fun getSHAForPublishedRiScOrNull(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ) = tryOrNull {
        getGithubResponse(
            uri = githubHelper.uriToFindRiSc(owner = owner, repository = repository, id = riScId),
            accessToken = accessToken,
        ).shaResponseDTO()
    }

    private suspend fun pullRequestForRiScExists(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): Boolean =
        fetchRiScIdentifiersSentForApproval(
            owner = owner,
            repository = repository,
            accessToken = accessToken,
        ).any { it.id == riScId }

    private fun fetchLatestShaForDefaultBranch(
        owner: String,
        repository: String,
        accessToken: String,
        defaultBranch: String,
    ): String? =
        getGithubResponse(
            uri =
                githubHelper.uriToGetCommitStatus(
                    owner = owner,
                    repository = repository,
                    branchName = defaultBranch,
                ),
            accessToken = accessToken,
        ).shaResponseDTO()

    fun createNewBranch(
        owner: String,
        repository: String,
        newBranchName: String,
        accessToken: String,
        defaultBranch: String,
    ): String? {
        val latestShaForDefaultBranch =
            fetchLatestShaForDefaultBranch(
                owner = owner,
                repository = repository,
                accessToken = accessToken,
                defaultBranch = defaultBranch,
            )
                ?: return null
        return postNewBranchToGithub(
            uri = githubHelper.uriToCreateNewBranchForRiSc(owner = owner, repository = repository),
            accessToken = accessToken,
            branchPayload =
                githubHelper.bodyToCreateNewBranchFromDefault(
                    branchName = newBranchName,
                    latestShaAtDefault = latestShaForDefaultBranch,
                ),
        ).bodyToMono<String>().block()
    }

    fun fetchAllPullRequests(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubPullRequestObject> =
        try {
            getGithubResponse(
                uri = githubHelper.uriToFetchAllPullRequests(owner = owner, repository = repository),
                accessToken = accessToken,
            ).toPullRequestResponseDTOs()
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchCommitsSinceLastCommit(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
        since: String,
    ): List<GithubCommitObject> =
        try {
            getGithubResponse(
                uri =
                    githubHelper.uriToFetchAllCommitsOnBranchSince(
                        owner = owner,
                        repository = repository,
                        branchName = riScId,
                        since = since,
                    ),
                accessToken = accessToken,
            ).bodyToMono<List<GithubCommitObject>>()
                .block() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchLatestCommitTimestampOnDefault(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
        branch: String,
    ): String? =
        tryOrNull {
            getGithubResponse(
                uri =
                    githubHelper.uriToFetchCommit(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                        branch = branch,
                    ),
                accessToken = accessToken,
            ).timeStampLatestCommitResponse()
        }

    fun createPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
        baseBranch: String,
    ): GithubPullRequestObject? =
        try {
            postNewPullRequestToGithub(
                uri = githubHelper.uriToCreatePullRequest(owner = owner, repository = repository),
                accessToken = accessTokens.githubAccessToken.value,
                pullRequestPayload =
                    githubHelper.bodyToCreateNewPullRequest(
                        repositoryOwner = owner,
                        branch = riScId,
                        requiresNewApproval = requiresNewApproval,
                        riScRiskOwner = userInfo,
                        baseBranch = baseBranch,
                    ),
            ).pullRequestResponseDTO()
        } catch (e: Exception) {
            throw CreatePullRequestException(
                message = "Failed with error ${e.message} when creating pull request for RiSc with id: $riScId",
                riScId = riScId,
            )
        }

    fun createPullRequestForSopsConfig(
        owner: String,
        repository: String,
        sopsId: String,
        gitHubAccessToken: GithubAccessToken,
        defaultBranch: String,
    ): GithubPullRequestObject? =
        postNewPullRequestToGithub(
            uri = githubHelper.uriToCreatePullRequest(owner = owner, repository = repository),
            accessToken = gitHubAccessToken.value,
            pullRequestPayload =
                GithubCreateNewPullRequestPayload(
                    title = "Update SOPS configuration",
                    body =
                        "This pull request updates the SOPS configuration that is needed to encrypt and decrypt RiSc's in " +
                            "[Risk Scorecard in Kartverket.dev](https://kartverket.dev/catalog/default/component/$repository/risc). " +
                            "Merge this PR in order to use the new SOPS configuration in the " +
                            "[Risk Scorecard plugin](https://kartverket.dev/catalog/default/component/$repository/risc).",
                    repositoryOwner = owner,
                    branch = sopsId,
                    baseBranch = defaultBranch,
                ),
        ).pullRequestResponseDTO()

    private fun postNewPullRequestToGithub(
        uri: String,
        accessToken: String,
        pullRequestPayload: GithubCreateNewPullRequestPayload,
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(pullRequestPayload.toContentBody()), String::class.java)
        .retrieve()

    private fun closePullRequest(
        uri: String,
        accessToken: String,
        closePullRequestBody: String,
    ) = webClient
        .patch()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(closePullRequestBody), String::class.java)
        .retrieve()

    private fun postNewBranchToGithub(
        uri: String,
        accessToken: String,
        branchPayload: GithubCreateNewBranchPayload,
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(branchPayload.toContentBody()), String::class.java)
        .retrieve()

    fun putFileRequestToGithub(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        filePath: String,
        branch: String,
        message: String,
        content: String,
    ): ResponseSpec {
        val payload =
            GithubWriteToFilePayload(
                message = message,
                content = content,
                sha =
                    fetchFileInfo(
                        repositoryOwner = repositoryOwner,
                        repositoryName = repositoryName,
                        gitHubAccessToken = gitHubAccessToken,
                        filePath = filePath,
                        branch = branch,
                    )?.sha,
                branchName = branch,
            )

        return try {
            webClient
                .put()
                .uri(githubHelper.repositoryContentsUri(repositoryOwner, repositoryName, filePath, branch))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token ${gitHubAccessToken.value}")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Content-Type", "application/json")
                .body(Mono.just(payload.toContentBody()), String::class.java)
                .retrieve()
        } catch (e: WebClientResponseException.BadRequest) {
            LOGGER.error("Got 400 bad request for filePath: $filePath with message: ${e.message}")
            throw e
        }
    }

    fun writeSopsConfig(
        sopsConfig: String,
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        branch: String,
    ): String =
        putFileRequestToGithub(
            repositoryOwner = repositoryOwner,
            repositoryName = repositoryName,
            gitHubAccessToken = gitHubAccessToken,
            filePath = "$riScFolderPath/.sops.yaml",
            branch = branch,
            message = "Update SOPS configuration",
            content = sopsConfig.encodeBase64(),
        ).bodyToMono<String>().block() ?: throw UnableToWriteSopsConfigException(
            message = "Failed to put new sops config on branch: '$branch'",
            response =
                ProcessRiScResultDTO(
                    riScId = "",
                    status = ProcessingStatus.FailedToCreateSops,
                    statusMessage = ProcessingStatus.FailedToCreateSops.message,
                ),
        )

    private fun fetchFileInfo(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        filePath: String,
        branch: String,
    ) = try {
        getGithubResponse(
            uri =
                githubHelper.repositoryContentsUri(
                    owner = repositoryOwner,
                    repository = repositoryName,
                    path = filePath,
                    branch = branch,
                ),
            accessToken = gitHubAccessToken.value,
        ).toFileContentDTO() ?: throw GitHubFetchException(
            message = "Unable to parse file information for file $filePath on $repositoryOwner/$repositoryName on branch: $branch",
            response =
                ProcessRiScResultDTO(
                    riScId = "",
                    status = ProcessingStatus.FailedToCreateSops,
                    statusMessage = ProcessingStatus.FailedToCreateSops.message,
                ),
        )
    } catch (e: WebClientResponseException.NotFound) {
        null
    }

    private suspend fun getGithubResponseSuspend(
        uri: String,
        accessToken: String,
    ): ResponseSpec {
        LOGGER.info("Sending GET-request to $uri")
        return webClient
            .get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()
    }

    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient
            .get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()

    private fun ResponseSpec.toPullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    private fun ResponseSpec.toPullRequestFilesDTO(): List<PullRequestFileObject>? = this.bodyToMono<List<PullRequestFileObject>>().block()

    fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.timeStampLatestCommitResponse(): String? =
        this
            .bodyToMono<List<GithubCommitObject>>()
            .block()
            ?.firstOrNull()
            ?.commit
            ?.committer
            ?.date

    private fun List<FileNameDTO>.riScIdentifiersPublished(): List<RiScIdentifier> =
        this
            .filter { it.value.endsWith(".$filenamePostfix.yaml") }
            .map { RiScIdentifier(it.value.substringBefore(".$filenamePostfix"), RiScStatus.Published) }

    private fun List<GithubPullRequestObject>.riScIdentifiersSentForApproval(): List<RiScIdentifier> =
        this
            .map {
                RiScIdentifier(
                    it.head.ref
                        .split("/")
                        .last(),
                    RiScStatus.SentForApproval,
                    it.url,
                )
            }.filter { it.id.startsWith("$filenamePrefix-") }

    private fun List<GithubReferenceObject>.riScIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

    private fun ResponseSpec.toFileContentDTO(): FileContentDTO? = this.bodyToMono<FileContentDTO>().block()

    private suspend fun ResponseSpec.decodedFileContentSuspend(): String? {
        val response = toEntity<FileContentDTO>().awaitSingle()
        LOGGER.info("GET to GitHub contents-API responded with ${response.statusCode}")
        val fileContentDTO: FileContentDTO? = response.body
        LOGGER.info("RiSc content: ${fileContentDTO?.content?.substring(0, 10)}")
        return fileContentDTO?.content?.decodeBase64()
    }

    private fun ResponseSpec.shaResponseDTO(): String? = this.bodyToMono<ShaResponseDTO>().block()?.value

    private fun ResponseSpec.toRepositoryBranchDTO(): List<RepositoryBranchDTO>? = this.bodyToMono<List<RepositoryBranchDTO>>().block()

    private fun ResponseSpec.toFileContentsDTO(): List<FileContentsDTO>? = this.bodyToMono<List<FileContentsDTO>>().block()

    private fun mapWebClientExceptionToGithubStatus(e: Exception): GithubStatus =
        if (e !is WebClientResponseException) {
            GithubStatus.InternalError
        } else {
            when (e) {
                is WebClientResponseException.NotFound -> GithubStatus.NotFound
                is WebClientResponseException.Unauthorized -> GithubStatus.Unauthorized
                is WebClientResponseException.UnprocessableEntity -> GithubStatus.RequestResponseBodyError
                else -> {
                    if (e.message.contains("DataBufferLimitException")) {
                        LOGGER.error(e.message)
                        GithubStatus.ResponseBodyTooLargeForWebClientError
                    } else {
                        GithubStatus.InternalError
                    }
                }
            }
        }

    private fun fetchRepositoryInfo(
        uri: String,
        gitHubAccessToken: String,
    ) = RestClient
        .builder()
        .baseUrl("https://api.github.com/repos")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "token $gitHubAccessToken")
        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.json")
        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
        .build()
        .get()
        .uri { uriBuilder ->
            uriBuilder
                .path(uri)
                .build()
        }.retrieve()
        .body(object : ParameterizedTypeReference<RepositoryDTO>() {})!!

    fun fetchRepositoryInfo(
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
    ): RepositoryInfo {
        val repositoryDTO =
            fetchRepositoryInfo(
                uri = githubHelper.uriToGetRepositoryInfo(owner = repositoryOwner, repository = repositoryName),
                gitHubAccessToken = gitHubAccessToken,
            )

        if (!repositoryDTO.permissions.pull) {
            throw PermissionDeniedOnGitHubException(
                "Request on $repositoryOwner/$repositoryName denied since user did not have pull or push permissions",
            )
        }

        if (repositoryDTO.permissions.push) {
            return RepositoryInfo(
                defaultBranch = repositoryDTO.defaultBranch,
                permissions = GitHubPermission.entries.toList(),
            )
        }
        return RepositoryInfo(
            defaultBranch = repositoryDTO.defaultBranch,
            permissions = listOf(GitHubPermission.READ),
        )
    }

    fun fetchDefaultBranch(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: String,
    ) = fetchRepositoryInfo(
        uri = githubHelper.uriToGetRepositoryInfo(owner = repositoryOwner, repository = repositoryName),
        gitHubAccessToken = gitHubAccessToken,
    ).defaultBranch

    fun fetchFilesUpdatedInPullRequest(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        pullRequest: GithubPullRequestObject,
    ) = getGithubResponse(
        githubHelper.uriToFetchPullRequestFiles(
            owner = repositoryOwner,
            repository = repositoryName,
            pullRequestNumber = pullRequest.number,
        ),
        gitHubAccessToken.value,
    ).toPullRequestFilesDTO() ?: throw GitHubFetchException(
        "Unable to fetch files changed in pull request number ${pullRequest.number} for $repositoryOwner/$repositoryName",
        ProcessRiScResultDTO(
            riScId = "",
            status = ProcessingStatus.FailedToCreateSops,
            statusMessage = ProcessingStatus.FailedToCreateSops.message,
        ),
    )

    fun fetchPullRequestsForBranches(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        defaultBranch: String,
        branches: List<String>,
    ): List<GithubPullRequestObject> =
        getGithubResponse(
            uri = githubHelper.uriToFetchAllPullRequests(owner = repositoryOwner, repository = repositoryName),
            accessToken = gitHubAccessToken.value,
        ).toPullRequestResponseDTOs()
            .filter { it.head.ref in branches && it.base.ref == defaultBranch }

    fun fetchAllBranches(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
    ) = getGithubResponse(
        uri = githubHelper.uriToFindAllBranches(owner = repositoryOwner, repository = repositoryName),
        accessToken = gitHubAccessToken.value,
    ).toRepositoryBranchDTO()

    fun fetchAllRiScsOnDefaultBranch(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
    ): Map<FileContentsDTO, String> {
        val fileContentPaths =
            try {
                getGithubResponse(
                    uri = githubHelper.uriToFindRiScFiles(owner = repositoryOwner, repository = repositoryName),
                    accessToken = gitHubAccessToken.value,
                ).toFileContentsDTO() ?: throw GitHubFetchException(
                    "Unable to fetch RiScs file paths on default branch for $repositoryOwner/$repositoryName",
                    ProcessRiScResultDTO(
                        riScId = "",
                        status = ProcessingStatus.FailedToCreateSops,
                        statusMessage = ProcessingStatus.FailedToCreateSops.message,
                    ),
                )
            } catch (e: WebClientResponseException.NotFound) {
                emptyList()
            }
        return fileContentPaths
            .filter { it.name.endsWith("$filenamePostfix.json") || it.name.endsWith("$filenamePostfix.yaml") }
            .associateWith {
                getGithubResponse(
                    uri =
                        githubHelper.repositoryContentsUri(
                            owner = repositoryOwner,
                            repository = repositoryName,
                            path = it.path,
                        ),
                    accessToken = gitHubAccessToken.value,
                ).toFileContentDTO()
                    ?.content
                    ?.decodeBase64() ?: throw GitHubFetchException(
                    message = "Unable to fetch RiScs file content from default branch for $repositoryOwner/$repositoryName",
                    response =
                        ProcessRiScResultDTO(
                            riScId = "",
                            status = ProcessingStatus.FailedToCreateSops,
                            statusMessage = ProcessingStatus.FailedToCreateSops.message,
                        ),
                )
            }
    }
}
