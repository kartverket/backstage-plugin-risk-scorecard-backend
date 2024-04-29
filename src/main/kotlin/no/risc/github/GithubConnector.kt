package no.risc.github

import no.risc.github.GithubHelper.toReferenceObjects
import no.risc.infra.connector.WebClientConnector
import no.risc.infra.connector.models.Email
import no.risc.infra.connector.models.UserContext
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.risc.models.FileContentDTO
import no.risc.risc.models.FileNameDTO
import no.risc.risc.models.ShaResponseDTO
import no.risc.utils.getFileNameWithHighestVersion
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Base64
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
            null -> "{\"message\":\"$message\", \"content\":\"$content\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email.value}\", \"date\":\"${author.formattedDate()}\" }"
            else -> "{\"message\":\"$message\", \"content\":\"$content\", \"sha\":\"$sha\", \"branch\": \"$branchName\", \"committer\": { \"name\":\"${author.name}\", \"email\":\"${author.email.value}\", \"date\":\"${author.formattedDate()}\" }"
        }
}

data class Author(val name: String, val email: Email, val date: Date) {
    fun formattedDate(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(date)
}

@Component
class GithubConnector(
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${filename.prefix}") val filenamePrefix: String,
    @Value("\${json.schema.path}") private val jsonSchemaPath: String,
) :
    WebClientConnector("https://api.github.com/repos") {
    fun fetchSopsConfig(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): String? {
        return try {
            getGithubResponse(
                GithubHelper.uriToFindSopsConfig(owner, repository, riScFolderPath),
                githubAccessToken.value,
            )
                .fileContent()
                ?.value
                ?.decodeBase64()
        } catch (e: Exception) {
            null
        }
    }

    private fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(this).decodeToString()

    fun fetchAllRiScIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRiScIdentifiersResponse {
        val draftRiScs =
            try {
                fetchRiScIdentifiersDrafted(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val publishedRiScs =
            try {
                fetchPublishedRiScIdentifiers(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val riScsSentForApproval =
            try {
                fetchRiScIdentifiersSentForApproval(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

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

    fun combinePublishedDraftAndSentForApproval(
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
                getGithubResponse(
                    "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml",
                    accessToken,
                ).fileContent()?.value

            if (fileContent == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                GithubContentResponse(
                    fileContent,
                    GithubStatus.Success,
                )
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    fun fetchJSONSchemas(): GithubContentResponse =
        try {
            val schemas =
                getGithubResponseNoAuth(
                    jsonSchemaPath,
                ).schemaContent()
            if (schemas == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                val latestVersion = getFileNameWithHighestVersion(schemas)
                val latestContent = fetchLatestJSONSchemaContent(latestVersion!!)

                GithubContentResponse(
                    latestContent.data,
                    GithubStatus.Success,
                )
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

    private fun fetchLatestJSONSchemaContent(schemaVersion: String) =
        try {
            val fileContent =
                getGithubResponseNoAuth(
                    "$jsonSchemaPath/$schemaVersion",
                ).rawFileContent()

            if (fileContent == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                GithubContentResponse(
                    fileContent,
                    GithubStatus.Success,
                )
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
                getGithubResponse(
                    uri =
                        GithubHelper.uriToFindContentOfFileOnDraftBranch(
                            owner,
                            repository,
                            id,
                            filenamePostfix = filenamePostfix,
                            riScFolderPath = riScFolderPath,
                        ),
                    accessToken = accessToken,
                ).fileContent()?.value

            if (fileContent == null) {
                GithubContentResponse(null, GithubStatus.ContentIsEmpty)
            } else {
                GithubContentResponse(
                    fileContent,
                    GithubStatus.Success,
                )
            }
        } catch (e: Exception) {
            GithubContentResponse(null, mapWebClientExceptionToGithubStatus(e))
        }

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

    private fun fetchPublishedRiScIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFindRiScFiles(owner, repository, riScFolderPath),
            accessToken,
        ).riScIdentifiersPublished()

    private fun fetchRiScIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFetchAllPullRequests(owner, repository),
            accessToken,
        ).pullRequestResponseDTOs().riScIdentifiersSentForApproval()

    private fun fetchRiScIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<RiScIdentifier> =
        getGithubResponse(
            uri = GithubHelper.uriToFindAllRiScBranches(owner, repository, riScFolderPath),
            accessToken = accessToken,
        ).toReferenceObjects().riScIdentifiersDrafted()

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        riScId: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        userContext: UserContext,
    ): Boolean {
        val accessToken = userContext.githubAccessToken
        val githubAuthor =
            Author(userContext.user.name, userContext.user.email, Date.from(Instant.now()))
        if (!branchForRiScDraftExists(owner, repository, riScId, accessToken.value)) {
            createNewBranch(
                owner = owner,
                repository = repository,
                riScId = riScId,
                accessToken = accessToken.value,
            )
        }
        var hasClosedPR = false
        if (pullRequestForRiScExists(owner, repository, riScId, accessToken.value) and requiresNewApproval) {
            closeExistingPullRequestForRiSc(owner, repository, riScId, accessToken.value)
            hasClosedPR = true
        }

        val latestShaForRiSc = getSHAForExistingRiScDraftOrNull(owner, repository, riScId, accessToken.value)

        val commitMessage =
            if (latestShaForRiSc != null) "refactor: Update RiSc with id: $riScId" else "feat: Create new RiSc with id: $riScId"

        putFileRequestToGithub(
            uri =
                GithubHelper.uriToPostContentOfFileOnDraftBranch(
                    owner,
                    repository,
                    riScId,
                    filenamePostfix = filenamePostfix,
                    riScFolderPath = riScFolderPath,
                ),
            accessToken.value,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = Base64.getEncoder().encodeToString(fileContent.toByteArray()),
                sha = latestShaForRiSc,
                branchName = riScId,
                author = githubAuthor,
            ),
        ).bodyToMono<String>().block()

        if (!requiresNewApproval and !pullRequestForRiScExists(owner, repository, riScId, accessToken.value)) {
            createPullRequestForPublishingRiSc(owner, repository, riScId, requiresNewApproval, userContext)
        }
        return hasClosedPR
    }

    private fun closeExistingPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? {
        val pullRequestsForRiSc = fetchAllPullRequestsForRiSc(owner, repository, accessToken)
        val matchingPullRequest = pullRequestsForRiSc.find { it.head.ref == riScId }
        if (matchingPullRequest != null) {
            try {
                return closePullRequest(
                    uri = GithubHelper.uriToEditPullRequest(owner, repository, matchingPullRequest.number),
                    accessToken = accessToken,
                    closePullRequestBody = GithubHelper.bodyToClosePullRequest(),
                ).bodyToMono<String>().block()
            } catch (e: Exception) {
                println(e)
            }
        }
        return null
    }

    private fun getSHAForExistingRiScDraftOrNull(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindContentOfFileOnDraftBranch(
                owner,
                repository,
                riScId,
                filenamePostfix = filenamePostfix,
                riScFolderPath = riScFolderPath,
            ),
            accessToken,
        ).shaReponseDTO()?.value
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
    ): Boolean {
        val riScsSentForApproval = fetchRiScIdentifiersSentForApproval(owner, repository, accessToken)
        return riScsSentForApproval.any { it.id == riScId }
    }

    private fun fetchBranchForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindExistingBranchForRiSc(owner, repository, riScId),
            accessToken,
        ).toReferenceObjects()
    } catch (e: Exception) {
        emptyList()
    }

    private fun fetchLatestShaForDefaultBranch(
        owner: String,
        repository: String,
        accessToken: String,
    ): String? =
        getGithubResponse(
            GithubHelper.uriToGetCommitStatus(owner, repository, "main"),
            accessToken,
        ).shaReponseDTO()?.value

    fun createNewBranch(
        owner: String,
        repository: String,
        riScId: String,
        accessToken: String,
    ): String? {
        val latestShaForMainBranch =
            fetchLatestShaForDefaultBranch(owner, repository, accessToken) ?: return null

        return postNewBranchToGithub(
            GithubHelper.uriToCreateNewBranchForRiSc(owner, repository),
            accessToken,
            GithubHelper.bodyToCreateNewBranchForRiScFromMain(riScId, latestShaForMainBranch),
        )
            .bodyToMono<String>()
            .block()
    }

    fun fetchAllPullRequestsForRiSc(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubPullRequestObject> {
        val githubResponse =
            try {
                getGithubResponse(
                    GithubHelper.uriToFetchAllPullRequests(owner, repository),
                    accessToken,
                ).pullRequestResponseDTOs()
            } catch (e: Exception) {
                emptyList()
            }

        return githubResponse
    }

    fun createPullRequestForPublishingRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        userContext: UserContext,
    ): GithubPullRequestObject? {
        return postNewPullRequestToGithub(
            GithubHelper.uriToCreatePullRequest(owner, repository),
            userContext.githubAccessToken.value,
            GithubHelper.bodyToCreateNewPullRequest(owner, riScId, requiresNewApproval, userContext.user),
        ).pullRequestResponseDTO()
    }

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

    private fun ResponseSpec.fileContent(): FileContentDTO? = this.bodyToMono<FileContentDTO>().block()

    private fun ResponseSpec.rawFileContent(): String? = this.bodyToMono<String>().block()

    private fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? = this.bodyToMono<ShaResponseDTO>().block()

    fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.riScIdentifiersPublished(): List<RiScIdentifier> =
        this.bodyToMono<List<FileNameDTO>>().block()
            ?.filter { it.value.endsWith(".$filenamePostfix.yaml") }
            ?.map { RiScIdentifier(it.value.substringBefore('.'), RiScStatus.Published) } ?: emptyList()

    fun List<GithubPullRequestObject>.riScIdentifiersSentForApproval(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.head.ref.split("/").last(), RiScStatus.SentForApproval) }
            .filter { it.id.startsWith("$filenamePrefix-") }

    fun List<GithubReferenceObject>.riScIdentifiersDrafted(): List<RiScIdentifier> =
        this.map { RiScIdentifier(it.ref.split("/").last(), RiScStatus.Draft) }

    fun ResponseSpec.schemaContent(): List<FileNameDTO>? = this.bodyToMono<List<FileNameDTO>>().block()

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
}
