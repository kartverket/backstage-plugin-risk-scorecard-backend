package no.risc.github.models

data class GithubContentResponse(
    val data: String?,
    val status: GithubStatus,
) {
    fun data(): String = data!!
}

enum class GithubStatus {
    NotFound,
    Unauthorized,
    ContentIsEmpty,
    Success,
    RequestResponseBodyError,
    ResponseBodyTooLargeForWebClientError,
    InternalError,
}

data class RiScApprovalPRStatus(
    val pullRequest: GithubPullRequestObject?,
    val hasClosedPr: Boolean,
)
