package no.risc.github

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.serialization.Serializable
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.github.models.FileContentDTO
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
import no.risc.utils.KDateSerializer
import no.risc.utils.decodeBase64
import no.risc.utils.encodeBase64
import no.risc.utils.tryOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.util.Date

@Serializable
data class GithubContentResponse(
    val data: String?,
    val status: GithubStatus,
) {
    fun data(): String = data!!
}

@Serializable
data class GithubRiScIdentifiersResponse(
    val ids: List<RiScIdentifier>,
    val status: GithubStatus,
)

@Serializable
enum class GithubStatus {
    NotFound,
    Unauthorized,
    ContentIsEmpty,
    Success,
    RequestResponseBodyError,
    ResponseBodyTooLargeForWebClientError,
    InternalError,
}

@Serializable
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

@Serializable
data class Author(
    val name: String?,
    val email: String?,
    @Serializable(KDateSerializer::class)
    val date: Date,
) {
    private fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)

    fun toJSONString(): String = "{ \"name\":\"${name}\", \"email\":\"${email}\", \"date\":\"${formattedDate()}\" }"
}

@Serializable
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
        val LOGGER: Logger = LoggerFactory.getLogger(GithubConnector::class.java)
    }

    /**
     * Fetches all RiSc identifiers in the given repository. There are three types, drafts (RiScs that have pending
     * updates), sent for approval (RiScs that have pending pull requests) and published (RiScs that have been approved,
     * i.e., appear in the default branch of the repository). If there exists multiple RiSc Identifiers with the same
     * ID, they are prioritised in the following order:
     *
     * 1. Sent for approval
     * 2. Draft
     * 3. Published
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository to fetch RiSc identifiers from.
     * @param accessToken The GitHub access token to use for authorization.
     */
    suspend fun fetchAllRiScIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRiScIdentifiersResponse =
        coroutineScope {
            val draftRiScs =
                async { fetchRiScIdentifiersDrafted(owner = owner, repository = repository, accessToken = accessToken) }
            val publishedRiScs =
                async { fetchPublishedRiScIdentifiers(owner = owner, repository = repository, accessToken = accessToken) }
            val riScsSentForApproval =
                async { fetchRiScIdentifiersSentForApproval(owner = owner, repository = repository, accessToken = accessToken) }

            GithubRiScIdentifiersResponse(
                status = GithubStatus.Success,
                ids =
                    combinePublishedDraftAndSentForApproval(
                        draftRiScList = draftRiScs.await(),
                        sentForApprovalList = riScsSentForApproval.await(),
                        publishedRiScList = publishedRiScs.await(),
                    ),
            )
        }

    /**
     * Combines the draft, published and sent for approval RiSc identifiers. Identifiers are added in the order:
     *
     * 1. Sent for approval
     * 2. Draft
     * 3. Published
     *
     * Later identifiers are ignored if there already exists an identifier with the same ID.
     *
     * @param draftRiScList RiSc identifiers for RiScs with pending changes
     * @param sentForApprovalList RiSc identifiers for RiScs with pending pull requests
     * @param publishedRiScList RiSc identifiers with RiSc files found in the default branch
     */
    private fun combinePublishedDraftAndSentForApproval(
        draftRiScList: List<RiScIdentifier>,
        sentForApprovalList: List<RiScIdentifier>,
        publishedRiScList: List<RiScIdentifier>,
    ): List<RiScIdentifier> =
        mutableMapOf<String, RiScIdentifier>()
            .also { identifiers ->
                sentForApprovalList.map { identifiers.putIfAbsent(it.id, it) }
                draftRiScList.map { identifiers.putIfAbsent(it.id, it) }
                publishedRiScList.map { identifiers.putIfAbsent(it.id, it) }
            }.values
            .toList()

    /**
     * Fetches the content of the RiSc at the given GitHub Contents-API uri.
     *
     * @param uri The GitHub Contents-API uri for the file of the given RiSc.
     * @param accessToken The GitHub access token to use for authorization.
     */
    private suspend fun fetchRiScContent(
        uri: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            getGithubResponse(uri = uri, accessToken = accessToken)
                .toEntity<FileContentDTO>()
                .awaitSingle()
                .also { LOGGER.info("GET to GitHub contents-API responded with ${it.statusCode}") }
                .body
                .also { LOGGER.info("RiSc content: ${it?.content?.substring(0, 10)}") }
                ?.content
                ?.decodeBase64()
                .let { fileContent ->
                    GithubContentResponse(
                        data = fileContent,
                        status = if (fileContent == null) GithubStatus.ContentIsEmpty else GithubStatus.Success,
                    )
                }
        } catch (e: Exception) {
            GithubContentResponse(data = null, status = mapWebClientExceptionToGithubStatus(e))
        }

    /**
     * Fetches the content of a published RiSc from the default branch of the given repository using the GitHub Contents-API.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository the RiSc belongs to.
     * @param id The ID of the RiSC.
     * @param accessToken The GitHub access token to use for authorization.
     */
    suspend fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        fetchRiScContent(
            uri = githubHelper.uriToFindRiSc(owner = owner, repository = repository, id = id),
            accessToken = accessToken,
        )

    /**
     * Fetches the content of the pending changes to a RiSc from the given repository using the GitHub Contents-API.
     *
     * @param owner The user/organisation the repository belongs to.
     * @param repository The repository the RiSc belongs to.
     * @param id The ID of the RiSC.
     * @param accessToken The GitHub access token to use for authorization.
     */
    suspend fun fetchDraftedRiScContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        fetchRiScContent(
            uri = githubHelper.uriToFindRiScOnDraftBranch(owner = owner, repository = repository, riScId = id),
            accessToken = accessToken,
        )

    /**
     * Finds the identifiers of every RiSc in a repository that is published, i.e., on the default branch.
     *
     * @param owner: The user/organisation the repository belongs to.
     * @param repository: The repository to check.
     * @param accessToken: The GitHub access token to use for authorization.
     */
    private suspend fun fetchPublishedRiScIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(uri = githubHelper.uriToFindRiScFiles(owner, repository), accessToken = accessToken)
                .awaitBody<List<FileNameDTO>>()
                // All RiSc files end in ".<filenamePostfix>.yaml".
                .filter { it.name.endsWith(".$filenamePostfix.yaml") }
                .map {
                    RiScIdentifier(
                        // The identifier of the RiSc is the part of the filename prior to ".<filenamePostfix>".
                        id = it.name.substringBefore(".$filenamePostfix"),
                        status = RiScStatus.Published,
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Finds the identifiers of every RiSc in a repository that has a pull request open.
     *
     * @param owner: The user/organisation the repository belongs to.
     * @param repository: The repository to check.
     * @param accessToken: The GitHub access token to use for authorization.
     */
    private suspend fun fetchRiScIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(
                uri = githubHelper.uriToFetchAllPullRequests(owner = owner, repository = repository),
                accessToken = accessToken,
            ).awaitBody<List<GithubPullRequestObject>>()
                .map {
                    RiScIdentifier(
                        // Want only the part after the last "/" in the branch path, ignoring "origin/", etc.
                        id = it.head.ref.substringAfterLast('/'),
                        status = RiScStatus.SentForApproval,
                        pullRequestUrl = it.url,
                    )
                    // Every RiSc identifier starts with "<filenamePrefix>-".
                }.filter { it.id.startsWith("$filenamePrefix-") }
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Finds the identifiers of every RiSc in a repository that has pending changes that have not been published to the
     * default branch. These are all RiScs that have a separate branch connect to them.
     *
     * @param owner: The user/organisation the repository belongs to.
     * @param repository: The repository to check.
     * @param accessToken: The GitHub access token to use for authorization.
     */
    private suspend fun fetchRiScIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(
                // This URI retrieves only branches that start with "<filenamePrefix>-"
                uri = githubHelper.uriToFindAllRiScBranches(owner = owner, repository = repository),
                accessToken = accessToken,
            ).awaitBody<List<GithubReferenceObjectDTO>>()
                // Want only the part after the last "/" in the branch path, ignoring "origin/", etc.
                .map { RiScIdentifier(id = it.ref.substringAfterLast('/'), status = RiScStatus.Draft) }
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
            val lastPublishedDate =
                getGithubResponse(
                    githubHelper.uriToFetchCommits(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                    ),
                    accessToken,
                ).awaitBody<List<GithubRefCommitDTO>>().first().commit.committer.dateTime

            val commits =
                getGithubResponse(
                    githubHelper.uriToFetchCommitsSince(
                        owner = owner,
                        repository = repository,
                        since = lastPublishedDate,
                    ),
                    accessToken,
                ).awaitBody<List<GithubRefCommitDTO>>()

            val commitsAfterPublish =
                commits.filter {
                    it.commit.committer.dateTime
                        .isAfter(lastPublishedDate)
                }

            LastPublished(lastPublishedDate, commitsAfterPublish.size)
        }

    internal suspend fun updateOrCreateDraft(
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
            coroutineScope {
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
        ).awaitBodyOrNull<String>()

        val riScApprovalPRStatus =
            coroutineScope {
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
                            gitHubAccessToken = accessToken,
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

    private suspend fun closePullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? =
        fetchAllPullRequests(owner, repository, accessToken).find { it.head.ref == riScId }?.let {
            try {
                requestToGithubWithJSONBody(
                    uri = githubHelper.uriToEditPullRequest(owner, repository, it.number),
                    accessToken = accessToken,
                    content = githubHelper.bodyToClosePullRequest(),
                    method = HttpMethod.PATCH,
                ).awaitBodyOrNull<String>()
            } catch (e: Exception) {
                LOGGER.error("Could not close pull request #${it.number} with error message: ${e.message}.")
                null
            }
        }

    private suspend fun getSHAForExistingRiScDraftOrNull(
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

    private suspend fun getSHAForPublishedRiScOrNull(
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

    private suspend fun fetchLatestShaForDefaultBranch(
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

    /**
     * Creates a new branch through the GitHub API by branching out of the default branch.
     *
     * @param owner: The owner (user/organisation) of the repository.
     * @param repository: The name of the repository to make the branch in.
     * @param newBranchName: The name of the new branch.
     * @param accessToken: The GitHub access token to use for authorization.
     * @param defaultBranch: The name of the default branch.
     */
    suspend fun createNewBranch(
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
            ) ?: return null

        return requestToGithubWithJSONBody(
            uri = githubHelper.uriToCreateNewBranch(owner = owner, repository = repository),
            accessToken = accessToken,
            content =
                githubHelper
                    .bodyToCreateNewBranchFromDefault(
                        branchName = newBranchName,
                        latestShaAtDefault = latestShaForDefaultBranch,
                    ).toContentBody(),
            method = HttpMethod.POST,
        ).awaitBodyOrNull<String>()
    }

    suspend fun fetchAllPullRequests(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubPullRequestObject> =
        try {
            getGithubResponse(
                uri = githubHelper.uriToFetchAllPullRequests(owner = owner, repository = repository),
                accessToken = accessToken,
            ).awaitBody<List<GithubPullRequestObject>>()
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun fetchCommitsSinceLastCommit(
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
            ).awaitBodyOrNull<List<GithubCommitObject>>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun fetchLatestCommitTimestampOnDefault(
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

    /**
     * Creates a pull request for the changes to a RiSc with the given riScId. That is, a pull request is created from
     * the branch `riScId` to `baseBranch` with a title and text dependent on if the changes have been approved or do
     * not require approval
     *
     * @param owner: The user/organisation that own the repository to make the pull request in.
     * @param repository: The repository to make the pull request in.
     * @param riScId: The id of the RiSc.
     * @param requiresNewApproval Indicates if the changes to the new.
     * @param gitHubAccessToken: The GitHub access token for authorization.
     * @param userInfo: Information about the user that is creating the pull request, i.e., the user who has approved the changes.
     * @param baseBranch: The branch to make the pull request to.
     * @throws CreatePullRequestException If creation of the pull request failed.
     */
    suspend fun createPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        gitHubAccessToken: String,
        userInfo: UserInfo,
        baseBranch: String,
    ): GithubPullRequestObject =
        createNewPullRequest(
            owner = owner,
            repository = repository,
            accessToken = gitHubAccessToken,
            pullRequestPayload =
                GithubCreateNewPullRequestPayload(
                    title = "Updated risk scorecard",
                    repositoryOwner = owner,
                    body =
                        if (requiresNewApproval) {
                            "${userInfo.name} (${userInfo.email}) has approved the risk scorecard. " +
                                "Merge the pull request to include the changes in the default branch."
                        } else {
                            "The risk scorecard has been updated, but does not require new approval."
                        },
                    branch = riScId,
                    baseBranch = baseBranch,
                ),
        )

    /**
     * Creates a request to create a new pull request through the GitHub API.
     *
     * @param owner: The owner (user/organisation) of the repository.
     * @param repository: The name of the repository to make the pull request in.
     * @param accessToken: The GitHub access token to use for authorization.
     * @param pullRequestPayload: The content of the pull request.
     * @throws CreatePullRequestException If creation of the pull request failed.
     */
    private suspend fun createNewPullRequest(
        owner: String,
        repository: String,
        accessToken: String,
        pullRequestPayload: GithubCreateNewPullRequestPayload,
    ): GithubPullRequestObject =
        try {
            requestToGithubWithJSONBody(
                uri = githubHelper.uriToCreatePullRequest(owner = owner, repository = repository),
                accessToken = accessToken,
                content = pullRequestPayload.toContentBody(),
                method = HttpMethod.POST,
            ).awaitBody<GithubPullRequestObject>()
        } catch (e: Exception) {
            throw CreatePullRequestException(
                message =
                    "Failed with error ${e.message} when creating pull request from branch ${pullRequestPayload.branch}" +
                        "to ${pullRequestPayload.baseBranch} with title \"${pullRequestPayload.title}\"",
            )
        }

    suspend fun putFileRequestToGithub(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: GithubAccessToken,
        filePath: String,
        branch: String,
        message: String,
        content: String,
    ): ResponseSpec =
        try {
            requestToGithubWithJSONBody(
                uri = githubHelper.repositoryContentsUri(repositoryOwner, repositoryName, filePath, branch),
                accessToken = gitHubAccessToken.value,
                content =
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
                    ).toContentBody(),
                method = HttpMethod.PUT,
            )
        } catch (e: WebClientResponseException.BadRequest) {
            LOGGER.error("Got 400 bad request for filePath: $filePath with message: ${e.message}")
            throw e
        }

    private suspend fun fetchFileInfo(
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
        ).awaitBodyOrNull<FileContentDTO>() ?: throw GitHubFetchException(
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

    /**
     * Constructs a request to the given URI at the GitHub API with standard headers:
     * - Accept: application/vnd.github.json
     * - Authorization: token <accessToken>
     * - X-GitHub-Api-Version: <current-GitHub-api-version>
     *
     * @param uri: The URI at GitHub to use ("https://api.github.com/repos$uri").
     * @param accessToken: The GitHub Access Token to use for authorization.
     * @param method: The HTTP method to make the request with.
     * @param attachBody: A method that attaches a body to the request if supplied (must attach body and "Content-Type" header).
     */
    private suspend inline fun githubRequest(
        uri: String,
        accessToken: String,
        method: HttpMethod,
        attachBody: (RequestBodySpec) -> RequestHeadersSpec<*> = { it },
    ): ResponseSpec =
        webClient
            .method(method)
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .let(attachBody)
            .retrieve()
            .also { LOGGER.info("Sending ${method.name()}-request to $uri") }

    /**
     * Constructs a GET-request to the specified URI at GitHub with standard headers.
     *
     * @param uri: The URI at GitHub to use ("https://api.github.com/repos$uri").
     * @param accessToken: The GitHub Access Token to use for authorization.
     */
    private suspend fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec = githubRequest(uri = uri, accessToken = accessToken, method = HttpMethod.GET)

    /**
     * Constructs a request to the specified URI at GitHub with standard headers and the provided JSON body. Uses
     * the supplied HTTP method to make the call.
     *
     * @param uri: The URI at GitHub to use ("https://api.github.com/repos$uri").
     * @param accessToken: The GitHub Access Token to use for authorization.
     * @param content: The JSON formatted content to send as the body of the request.
     * @param method: The HTTP method to make the call with.
     */
    private suspend fun requestToGithubWithJSONBody(
        uri: String,
        accessToken: String,
        content: String,
        method: HttpMethod,
    ): ResponseSpec =
        githubRequest(uri = uri, accessToken = accessToken, method = method, attachBody = {
            it.header("Content-Type", "application/json").body(Mono.just(content), String::class.java)
        })

    private suspend fun ResponseSpec.timeStampLatestCommitResponse(): String? =
        this
            .awaitBodyOrNull<List<GithubCommitObject>>()
            ?.firstOrNull()
            ?.commit
            ?.committer
            ?.date

    private suspend fun ResponseSpec.shaResponseDTO(): String? = awaitBodyOrNull<ShaResponseDTO>()?.sha

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

    /**
     * Retrieves information about the given repository "/<repositoryOwner>/<repositoryName>". The information contains
     * information about the default branch and which permissions the user for the repository.
     *
     * @param repositoryOwner The name of the user/organisation owning the repository
     * @param repositoryName The name of the repository to fetch information for
     * @param gitHubAccessToken The GitHub access token to use for fetching the information
     * @throws PermissionDeniedOnGitHubException when the GitHub access token used does not have read access to the repository.
     */
    suspend fun fetchRepositoryInfo(
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
    ): RepositoryInfo {
        val repositoryDTO =
            getGithubResponse(
                uri = githubHelper.uriToGetRepositoryInfo(owner = repositoryOwner, repository = repositoryName),
                accessToken = gitHubAccessToken,
            ).awaitBody<RepositoryDTO>()

        if (!repositoryDTO.permissions.pull) {
            throw PermissionDeniedOnGitHubException(
                "Request on $repositoryOwner/$repositoryName denied since user did not have pull or push permissions",
            )
        }

        return RepositoryInfo(
            defaultBranch = repositoryDTO.defaultBranch,
            permissions =
                if (repositoryDTO.permissions.push) {
                    GitHubPermission.entries.toList()
                } else {
                    listOf(GitHubPermission.READ)
                },
        )
    }

    /**
     * Finds the default branch for the given repository "/<repositoryOwner>/<repositoryName>".
     *
     * @param repositoryOwner The name of the user/organisation owning the repository
     * @param repositoryName The name of the repository to fetch information for
     * @param gitHubAccessToken The GitHub access token to use for fetching the information
     * @throws PermissionDeniedOnGitHubException when the GitHub access token used does not have read access to the repository.
     */
    suspend fun fetchDefaultBranch(
        repositoryOwner: String,
        repositoryName: String,
        gitHubAccessToken: String,
    ) = fetchRepositoryInfo(
        repositoryOwner = repositoryOwner,
        repositoryName = repositoryName,
        gitHubAccessToken = gitHubAccessToken,
    ).defaultBranch
}
