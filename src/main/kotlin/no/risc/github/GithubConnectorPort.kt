package no.risc.github

import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.RiScApprovalPRStatus
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.infra.connector.models.RepositoryInfo
import no.risc.risc.models.DeleteRiScResultDTO
import no.risc.risc.models.LastPublished
import no.risc.risc.models.UserInfo

interface GithubConnectorPort {
    suspend fun fetchRiScGithubMetadata(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): List<RiScGithubMetadata>

    suspend fun fetchBranchAndMainRiScContent(
        riScId: String,
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): RiScMainAndBranchContent

    suspend fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse

    suspend fun fetchLastPublishedRiScDateAndCommitNumber(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
    ): LastPublished?

    suspend fun updateOrCreateDraft(
        owner: String,
        repository: String,
        riScId: String,
        defaultBranch: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        gitHubAccessToken: GithubAccessToken,
        userInfo: UserInfo,
    ): RiScApprovalPRStatus

    suspend fun deleteRiSc(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
    ): DeleteRiScResultDTO

    suspend fun createPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        gitHubAccessToken: String,
        userInfo: UserInfo,
    ): GithubPullRequestObject

    suspend fun fetchRepositoryInfo(
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
    ): RepositoryInfo
}
