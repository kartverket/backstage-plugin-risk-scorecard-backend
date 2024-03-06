package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.kvros.github.GithubHelper.toReferenceObjects
import no.kvros.infra.connector.WebClientConnector
import no.kvros.infra.connector.models.Email
import no.kvros.infra.connector.models.UserContext
import no.kvros.ros.ROSIdentifier
import no.kvros.ros.ROSStatus
import no.kvros.ros.models.FileContentDTO
import no.kvros.ros.models.ROSFilenameDTO
import no.kvros.ros.models.ShaResponseDTO
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

data class GithubRosIdentifiersResponse(
    val ids: List<ROSIdentifier>,
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class SopsConfig(
    val creation_rules: List<CreationRules>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreationRules(
    val key_groups: List<KeyGroup>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KeyGroup(
    val age: List<String>?,
    val gcp_kms: List<ResourceId>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ResourceId(
    val resource_id: String,
)

@Component
class GithubConnector(
    @Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String,
) :
    WebClientConnector("https://api.github.com/repos") {
    private val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    fun fetchSopsConfig(
        owner: String,
        repository: String,
        accessToken: String,
    ): String? {
        return try {
            getGithubResponse(GithubHelper.uriToFindSopsConfig(owner, repository), accessToken)
                .fileContent()
                ?.content
                ?.decodeBase64()
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <reified T> String.parseYaml(): T = mapper.readValue(this, T::class.java)

    private fun String.decodeBase64(): String = Base64.getMimeDecoder().decode(this).decodeToString()

    private fun SopsConfig.getKeyGroups(): List<KeyGroup> = this.creation_rules.first().key_groups

    fun fetchAllRosIdentifiersInRepository(
        owner: String,
        repository: String,
        accessToken: String,
    ): GithubRosIdentifiersResponse {
        val draftROSes =
            try {
                fetchROSIdentifiersDrafted(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val publishedROSes =
            try {
                fetchPublishedROSIdentifiers(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        val rosSentForApproval =
            try {
                fetchROSIdentifiersSentForApproval(owner, repository, accessToken)
            } catch (e: Exception) {
                emptyList()
            }

        return GithubRosIdentifiersResponse(
            status = GithubStatus.Success,
            ids =
                combinePublishedDraftAndSentForApproval(
                    draftRosList = draftROSes,
                    sentForApprovalList = rosSentForApproval,
                    publishedRosList = publishedROSes,
                ),
        )
    }

    fun combinePublishedDraftAndSentForApproval(
        draftRosList: List<ROSIdentifier>,
        sentForApprovalList: List<ROSIdentifier>,
        publishedRosList: List<ROSIdentifier>,
    ): List<ROSIdentifier> {
        val draftIds = draftRosList.map { it.id }
        val sentForApprovalsIds = sentForApprovalList.map { it.id }
        val publishedROSIdentifiersNotInDraftList =
            publishedRosList.filter { it.id !in draftIds && it.id !in sentForApprovalsIds }
        val draftROSIdentifiersNotInSentForApprovalsList = draftRosList.filter { it.id !in sentForApprovalsIds }

        return sentForApprovalList + publishedROSIdentifiersNotInDraftList + draftROSIdentifiersNotInSentForApprovalsList
    }

    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(
                    "/$owner/$repository/contents/$defaultROSPath/$id.ros.yaml",
                    accessToken,
                ).fileContent()?.content

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

    fun fetchDraftedROSContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse =
        try {
            val fileContent =
                getGithubResponse(
                    uri = GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, id),
                    accessToken = accessToken,
                ).fileContent()?.content

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

    private fun fetchPublishedROSIdentifiers(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFindRosFiles(owner, repository, defaultROSPath),
            accessToken,
        ).rosIdentifiersPublished()

    private fun fetchROSIdentifiersSentForApproval(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> =
        getGithubResponse(
            GithubHelper.uriToFetchAllPullRequests(owner, repository),
            accessToken,
        ).pullRequestResponseDTOs().rosIdentifiersSentForApproval()

    private fun fetchROSIdentifiersDrafted(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<ROSIdentifier> =
        getGithubResponse(
            uri = GithubHelper.uriToFindAllRosBranches(owner, repository),
            accessToken = accessToken,
        ).toReferenceObjects().rosIdentifiersDrafted()

    internal fun updateOrCreateDraft(
        owner: String,
        repository: String,
        rosId: String,
        fileContent: String,
        userContext: UserContext,
    ): String? {
        val accessToken = userContext.githubAccessToken
        val githubAuthor =
            Author(userContext.microsoftUser.name, userContext.microsoftUser.email, Date.from(Instant.now()))
        if (!branchForROSDraftExists(owner, repository, rosId, accessToken.value)) {
            createNewBranch(
                owner = owner,
                repository = repository,
                rosId = rosId,
                accessToken = accessToken.value,
            )
        }

        val latestShaForROS = getSHAForExistingROSDraftOrNull(owner, repository, rosId, accessToken.value)

        val commitMessage = if (latestShaForROS != null) "refactor: Oppdater ROS" else "feat: Lag ny ROS"

        return putFileRequestToGithub(
            uri = GithubHelper.uriToPostContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken.value,
            GithubWriteToFilePayload(
                message = commitMessage,
                content = Base64.getEncoder().encodeToString(fileContent.toByteArray()),
                sha = latestShaForROS,
                branchName = rosId,
                author = githubAuthor,
            ),
        ).bodyToMono<String>().block()
    }

    private fun getSHAForExistingROSDraftOrNull(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindContentOfFileOnDraftBranch(owner, repository, rosId),
            accessToken,
        ).shaReponseDTO()?.value
    } catch (e: Exception) {
        null
    }

    private fun branchForROSDraftExists(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ): Boolean = fetchBranchForROS(owner, repository, rosId, accessToken).isNotEmpty()

    private fun fetchBranchForROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
    ) = try {
        getGithubResponse(
            GithubHelper.uriToFindExistingBranchForROS(owner, repository, rosId),
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
        rosId: String,
        accessToken: String,
    ): String? {
        val latestShaForMainBranch =
            fetchLatestShaForDefaultBranch(owner, repository, accessToken) ?: return null

        return postNewBranchToGithub(
            GithubHelper.uriToCreateNewBranchForROS(owner, repository),
            accessToken,
            GithubHelper.bodyToCreateNewBranchForROSFromMain(rosId, latestShaForMainBranch),
        )
            .bodyToMono<String>()
            .block()
    }

    fun fetchAllPullRequestsForROS(
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

    fun createPullRequestForPublishingROS(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: GithubAccessToken,
    ): GithubPullRequestObject? {
        return postNewPullRequestToGithub(
            GithubHelper.uriToCreatePullRequest(owner, repository),
            accessToken.value,
            GithubHelper.bodyToCreateNewPullRequest(owner, rosId),
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

    private fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? = this.bodyToMono<ShaResponseDTO>().block()

    fun ResponseSpec.pullRequestResponseDTOs(): List<GithubPullRequestObject> =
        this.bodyToMono<List<GithubPullRequestObject>>().block() ?: emptyList()

    fun ResponseSpec.pullRequestResponseDTO(): GithubPullRequestObject? = this.bodyToMono<GithubPullRequestObject>().block()

    private fun ResponseSpec.rosIdentifiersPublished(): List<ROSIdentifier> =
        this.bodyToMono<List<ROSFilenameDTO>>().block()
            ?.filter { it.name.endsWith(".ros.yaml") }
            ?.map { ROSIdentifier(it.name.substringBefore('.'), ROSStatus.Published) } ?: emptyList()

    fun List<GithubPullRequestObject>.rosIdentifiersSentForApproval(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.head.ref.split("/").last(), ROSStatus.SentForApproval) }
            .filter { it.id.startsWith("ros-") }

    fun List<GithubReferenceObject>.rosIdentifiersDrafted(): List<ROSIdentifier> =
        this.map { ROSIdentifier(it.ref.split("/").last(), ROSStatus.Draft) }

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
}
