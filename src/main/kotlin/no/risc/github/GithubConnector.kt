package no.risc.github

import net.pwall.log.getLogger
import no.risc.exception.exceptions.SopsConfigFetchException
import no.risc.github.models.FileContentDTO
import no.risc.github.models.FileNameDTO
import no.risc.github.models.ShaResponseDTO
import no.risc.infra.connector.WebClientConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.risc.models.UserInfo
import no.risc.utils.decodeBase64
import no.risc.utils.encodeBase64
import no.risc.utils.getFileNameWithHighestVersion
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.time.Instant
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
    InternalError, // TODO
}

data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branchName: String,
    val author: Author,
) {
    fun toContentBody(): String =
        when (sha) {
            null -> "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email}\", \"date\":\"${author.formattedDate()}\" }"
            else -> "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email}\", \"date\":\"${author.formattedDate()}\" }, \"sha\":\"$sha\""
        }
}

data class Author(val name: String, val email: String, val date: Date) {
    fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)
}

@Component
class GithubConnector(
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    @Value("\${json.schema.path}") private val jsonSchemaPath: String,
    private val githubHelper: GithubHelper,
) :
    WebClientConnector("https://api.github.com/repos") {
    fun fetchSopsConfig(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
        riScId: String
    ): GithubContentResponse =
        try {
            val sopsConfigResponse = getGithubResponse(
                    githubHelper.uriToFindSopsConfig(owner, repository),
                    githubAccessToken.value,
                )
            when (sopsConfigResponse.decodedFileContent()) {
                null -> throw SopsConfigFetchException(
                    message = "Failed to fetch sops config from location: ${githubHelper.uriToFindSopsConfig(owner, repository)} with the following response: $sopsConfigResponse",
                    riScId = riScId,
                    responseMessage = "Could not fetch SOPS config"
                )
                else -> GithubContentResponse(sopsConfigResponse.decodedFileContent(), GithubStatus.Success)
            }
        } catch (e: Exception) {
            throw SopsConfigFetchException(
                message = e.message ?: "Could not fetch SOPS config",
                riScId = riScId,
                responseMessage = "Could not fetch SOPS config"
            )
        }

    fun fetchAllRiScIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRiScIdentifiersResponse {
        val draftRiScs = fetchRiScIdentifiersDrafted(owner, repository, accessToken)
        val publishedRiScs = fetchPublishedRiScIdentifiers(owner, repository, accessToken)
        val riScsSentForApproval = fetchRiScIdentifiersSentForApproval(owner, repository, accessToken)

        return GithubRiScIdentifiersResponse(
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
        val draftIds = draftRiScList.map { it.id }
        val sentForApprovalsIds = sentForApprovalList.map { it.id }
        val publishedRiScIdentifiersNotInDraftList =
            publishedRiScList.filter { it.id !in draftIds && it.id !in sentForApprovalsIds }
        val draftRiScIdentifiersNotInSentForApprovalsList = draftRiScList.filter { it.id !in sentForApprovalsIds }

        return sentForApprovalList + publishedRiScIdentifiersNotInDraftList + draftRiScIdentifiersNotInSentForApprovalsList
    }

    fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(githubHelper.uriToFindRiSc(owner, repository, id), accessToken).decodedFileContent()
            when (fileContent) {
                null -> GithubContentResponse(null, GithubStatus.ContentIsEmpty)
                else -> GithubContentResponse(fileContent, GithubStatus.Success)
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    fun fetchLatestJSONSchema(): GithubContentResponse =
        try {
            getGithubResponseNoAuth(jsonSchemaPath).schemaFilenames()?.let {
                getFileNameWithHighestVersion(it)?.let { version ->
                    fetchJSONSchema(version)
                }
            } ?: GithubContentResponse(null, GithubStatus.NotFound)
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    fun fetchJSONSchema(filename: String): GithubContentResponse =
        try {
            when (val fileContent = getGithubResponseNoAuth("$jsonSchemaPath/$filename").rawFileContent()) {
                null -> GithubContentResponse(null, GithubStatus.ContentIsEmpty)
                else -> GithubContentResponse(fileContent, GithubStatus.Success)
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    fun fetchDraftedRiScContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(githubHelper.uriToFindRiScOnDraftBranch(owner, repository, id), accessToken)
                    .decodedFileContent()

            when (fileContent) {
                null -> GithubContentResponse(null, GithubStatus.ContentIsEmpty)
                else -> GithubContentResponse(fileContent, GithubStatus.Success)
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    private fun fetchPublishedRiScIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(githubHelper.uriToFindRiScFiles(owner, repository), accessToken)
                .riScIdentifiersPublished()
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchRiScIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(githubHelper.uriToFetchAllPullRequests(owner, repository), accessToken)
                .pullRequestResponseDTOs().riScIdentifiersSentForApproval()
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchRiScIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        try {
            getGithubResponse(
                githubHelper.uriToFindAllRiScBranches(owner, repository),
                accessToken,
            ).toReferenceObjects().riScIdentifiersDrafted()
        } catch (e: Exception) {
            emptyList()
        }

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        riScId: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
    ): Boolean {
        val accessToken = accessTokens.githubAccessToken.value
        val githubAuthor = Author(userInfo.name, userInfo.email, Date.from(Instant.now()))
        if (!branchForRiScDraftExists(owner, repository, riScId, accessToken)) {
            createNewBranch(owner, repository, riScId, accessToken)
        }

        val latestShaForRiSc = getSHAForExistingRiScDraftOrNull(owner, repository, riScId, accessToken)

        val commitMessage =
            when (latestShaForRiSc) {
                null -> "Create new RiSc with id: $riScId"
                else -> "Update RiSc with id: $riScId"
            }

        putFileRequestToGithub(
            githubHelper.uriToPutRiScOnDraftBranch(owner, repository, riScId),
            accessToken,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = fileContent.encodeBase64(),
                sha = latestShaForRiSc,
                branchName = riScId,
                author = githubAuthor,
            ),
        ).bodyToMono<String>().block()

        val prExists = pullRequestForRiScExists(owner, repository, riScId, accessToken)
        if (!requiresNewApproval and !prExists) {
            createPullRequestForRiSc(owner, repository, riScId, requiresNewApproval, accessTokens, userInfo)
        }
        if (requiresNewApproval and prExists) {
            closePullRequestForRiSc(owner, repository, riScId, accessToken)
        }

        return requiresNewApproval and prExists
    }

    private fun closePullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? =
        fetchAllPullRequestsForRiSc(owner, repository, accessToken).find { it.head.ref == riScId }?.let {
            try {
                closePullRequest(
                    uri = githubHelper.uriToEditPullRequest(owner, repository, it.number),
                    accessToken = accessToken,
                    closePullRequestBody = githubHelper.bodyToClosePullRequest(),
                ).bodyToMono<String>().block()
            } catch (e: Exception) {
                getLogger().error("Could not close pull request with error message: ${e.message}.")
                null
            }
        }

    private fun getSHAForExistingRiScDraftOrNull(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(githubHelper.uriToFindRiScOnDraftBranch(owner, repository, riScId), accessToken)
            .shaResponseDTO()
    } catch (e: Exception) {
        null
    }

    private fun branchForRiScDraftExists(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): Boolean = fetchBranchForRiSc(owner, repository, riScId, accessToken).isNotEmpty()

    private fun pullRequestForRiScExists(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): Boolean = fetchRiScIdentifiersSentForApproval(owner, repository, accessToken).any { it.id == riScId }

    private fun fetchBranchForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): List<GithubReferenceObject> =
        try {
            getGithubResponse(githubHelper.uriToFindExistingBranchForRiSc(owner, repository, riScId), accessToken)
                .toReferenceObjects()
        } catch (e: Exception) {
            emptyList()
        }

    private fun fetchLatestShaForDefaultBranch(
        owner: String,
        repository: String,
        accessToken: String,
    ): String? = getGithubResponse(githubHelper.uriToGetCommitStatus(owner, repository, "main"), accessToken).shaResponseDTO()

    fun createNewBranch(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? {
        val latestShaForMainBranch = fetchLatestShaForDefaultBranch(owner, repository, accessToken) ?: return null
        return postNewBranchToGithub(
            uri = githubHelper.uriToCreateNewBranchForRiSc(owner, repository),
            accessToken = accessToken,
            branchPayload = githubHelper.bodyToCreateNewBranchForRiScFromMain(riScId, latestShaForMainBranch),
        ).bodyToMono<String>().block()
    }

    fun fetchAllPullRequestsForRiSc(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubPullRequestObject> =
        try {
            getGithubResponse(githubHelper.uriToFetchAllPullRequests(owner, repository), accessToken)
                .pullRequestResponseDTOs()
        } catch (e: Exception) {
            emptyList()
        }

    fun createPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        accessTokens: AccessTokens,
        userInfo: UserInfo,
    ): GithubPullRequestObject? =
        postNewPullRequestToGithub(
            uri = githubHelper.uriToCreatePullRequest(owner, repository),
            accessToken = accessTokens.githubAccessToken.value,
            pullRequestPayload = githubHelper.bodyToCreateNewPullRequest(owner, riScId, requiresNewApproval, userInfo),
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

    private fun putFileRequestToGithub(
        uri: String,
        accessToken: String,
        writePayload: GithubWriteToFilePayload,
    ) = webClient
        .put()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(writePayload.toContentBody()), String::class.java)
        .retrieve()

    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()

    private fun getGithubResponseNoAuth(uri: String): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.raw+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()

    private fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    private fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.riScIdentifiersPublished(): List<RiScIdentifier> =
        this.bodyToMono<List<FileNameDTO>>().block()
            ?.filter { it.value.endsWith(".$filenamePostfix.yaml") }
            ?.map { RiScIdentifier(it.value.substringBefore(".$filenamePostfix"), RiScStatus.Published) } ?: emptyList()

    private fun List<GithubPullRequestObject>.riScIdentifiersSentForApproval(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.head.ref.split("/").last(), RiScStatus.SentForApproval) }
            .filter { it.id.startsWith("$filenamePrefix-") }

    private fun List<GithubReferenceObject>.riScIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

    private fun ResponseSpec.schemaFilenames(): List<FileNameDTO>? = this.bodyToMono<List<FileNameDTO>>().block()

    private fun ResponseSpec.decodedFileContent(): String? = this.bodyToMono<FileContentDTO>().block()?.value?.decodeBase64()

    private fun ResponseSpec.rawFileContent(): String? = this.bodyToMono<String>().block()

    private fun ResponseSpec.shaResponseDTO(): String? = this.bodyToMono<ShaResponseDTO>().block()?.value

    private fun ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    private fun mapWebClientExceptionToGithubStatus(e: Exception): GithubStatus =
        when (e) {
            is WebClientResponseException ->
                when (e) {
                    is WebClientResponseException.NotFound -> GithubStatus.NotFound
                    is WebClientResponseException.Unauthorized -> GithubStatus.Unauthorized
                    is WebClientResponseException.UnprocessableEntity -> GithubStatus.RequestResponseBodyError
                    else -> GithubStatus.InternalError
                }

            else -> GithubStatus.InternalError
        }
}
