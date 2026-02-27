package no.risc.github

import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubPullRequestBranch
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.GithubStatus
import no.risc.github.models.RiScApprovalPRStatus
import no.risc.infra.connector.models.GitHubPermission
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.infra.connector.models.RepositoryInfo
import no.risc.risc.models.DeleteRiScResultDTO
import no.risc.risc.models.LastPublished
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.UserInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Local filesystem-based implementation of [GithubConnectorPort] for development without GitHub access.
 *
 * Activated by the `local-github` Spring profile. Files are stored under [storagePath]:
 * ```
 * <storagePath>/
 *   <repository>/
 *     branch/
 *       default/                     ← published RiScs
 *         <stem>.<postfix>.yaml
 *       <riScId>/                    ← draft branch (directory presence = branch exists)
 *         <stem>.<postfix>.yaml
 *     pr/
 *       <riScId>/                    ← open PR (directory presence = PR exists)
 * ```
 */
@Component
@Profile("local-github | local-sandboxed")
class LocalGithubConnector(
    @Value("\${filename.prefix}") private val branchPrefix: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
    @Value("\${github.local.storage-path:./local-github}") private val storagePath: String,
) : GithubConnectorPort {
    private val storageRoot: Path = Path.of(storagePath).toAbsolutePath().normalize()

    /**
     * Validates that [value] is a safe single path segment: non-blank, no path separators,
     * no parent-directory references, and only characters valid in GitHub repository names and
     * RiSc IDs. Throws [IllegalArgumentException] on any violation.
     *
     * This is the primary guard against path-traversal: tainted input is rejected here,
     * before it is passed to any [Path] API.
     */
    private fun requireSafePathSegment(
        value: String,
        name: String,
    ): String {
        require(value.isNotBlank()) { "$name must not be blank" }
        require(!value.contains('/') && !value.contains('\\')) {
            "$name must not contain path separators"
        }
        require(value != "." && value != "..") { "$name must not be '.' or '..'" }
        require(value.matches(Regex("^[A-Za-z0-9._-]+$"))) {
            "$name contains characters not allowed in a path segment"
        }
        return value
    }

    /** Secondary guard: normalises [this] path and throws if it escapes [storageRoot]. */
    private fun Path.requireUnderStorageRoot(): Path {
        val normalised = toAbsolutePath().normalize()
        require(normalised.startsWith(storageRoot)) { "Path traversal detected" }
        return normalised
    }

    private fun repoDir(repository: String): Path =
        storageRoot
            .resolve(requireSafePathSegment(repository, "repository"))
            .requireUnderStorageRoot()

    private fun branchDir(
        repository: String,
        branch: String,
    ): Path =
        repoDir(repository)
            .resolve("branch")
            .resolve(requireSafePathSegment(branch, "branch"))
            .requireUnderStorageRoot()

    private fun defaultBranchDir(repository: String): Path = branchDir(repository, "default")

    private fun prDir(
        repository: String,
        riScId: String,
    ): Path =
        repoDir(repository)
            .resolve("pr")
            .resolve(requireSafePathSegment(riScId, "riScId"))
            .requireUnderStorageRoot()

    /**
     * Derives the filename from riScId, mirroring [no.risc.github.GithubHelper.riscPath] logic.
     * For IDs with the form `<prefix>-<stem>-backstage_<...>`, the prefix is stripped so the
     * stored filename matches what GitHub would hold.
     */
    private fun riscFilename(riScId: String): String {
        val stem =
            if (riScId.startsWith("$branchPrefix-") && riScId.contains("-backstage_")) {
                riScId.removePrefix("$branchPrefix-")
            } else {
                riScId
            }
        return "$stem.$filenamePostfix.yaml"
    }

    /** Absolute path to the published (default-branch) file for [riScId]. */
    private fun publishedFilePath(
        repository: String,
        riScId: String,
    ): Path =
        defaultBranchDir(repository)
            .resolve(riscFilename(riScId))
            .requireUnderStorageRoot()

    /** Absolute path to the draft file for [riScId]. */
    private fun draftFilePath(
        repository: String,
        riScId: String,
    ): Path =
        branchDir(repository, riScId)
            .resolve(riscFilename(riScId))
            .requireUnderStorageRoot()

    private fun readFile(path: Path): GithubContentResponse =
        if (path.isRegularFile()) {
            GithubContentResponse(data = path.readText(), status = GithubStatus.Success)
        } else {
            GithubContentResponse(data = null, status = GithubStatus.NotFound)
        }

    override suspend fun fetchRepositoryInfo(
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
    ): RepositoryInfo =
        RepositoryInfo(
            defaultBranch = "default",
            permissions = GitHubPermission.entries.toList(),
        )

    override suspend fun fetchRiScGithubMetadata(
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): List<RiScGithubMetadata> {
        val publishedDir = defaultBranchDir(repository)
        val branchBaseDir = repoDir(repository).resolve("branch")
        val prBaseDir = repoDir(repository).resolve("pr")

        val mainIds: Set<String> =
            if (publishedDir.isDirectory()) {
                publishedDir
                    .listDirectoryEntries("*.$filenamePostfix.yaml")
                    .map { file ->
                        val fileId = file.name.substringBefore(".$filenamePostfix")
                        if (fileId.contains("-backstage_")) "$branchPrefix-$fileId" else fileId
                    }.toSet()
            } else {
                emptySet()
            }

        val branchIds: Set<String> =
            if (branchBaseDir.isDirectory()) {
                branchBaseDir
                    .listDirectoryEntries()
                    .filter { it.isDirectory() && it.name != "default" }
                    .map { it.name }
                    .toSet()
            } else {
                emptySet()
            }

        val prIds: Set<String> =
            if (prBaseDir.isDirectory()) {
                prBaseDir
                    .listDirectoryEntries()
                    .filter { it.isDirectory() }
                    .map { it.name }
                    .toSet()
            } else {
                emptySet()
            }

        val allIds = mainIds + branchIds
        return allIds.map { id ->
            RiScGithubMetadata(
                id = id,
                isStoredInMain = id in mainIds,
                hasBranch = id in branchIds,
                hasOpenPR = id in prIds,
                prUrl = if (id in prIds) prDir(repository, id).toUri().toString() else null,
            )
        }
    }

    override suspend fun fetchBranchAndMainRiScContent(
        riScId: String,
        owner: String,
        repository: String,
        githubAccessToken: GithubAccessToken,
    ): RiScMainAndBranchContent =
        RiScMainAndBranchContent(
            mainContent = readFile(publishedFilePath(repository, riScId)),
            branchContent = readFile(draftFilePath(repository, riScId)),
        )

    override suspend fun fetchPublishedRiSc(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): GithubContentResponse = readFile(publishedFilePath(repository, id))

    override suspend fun fetchLastPublishedRiScDateAndCommitNumber(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
    ): LastPublished? = null

    override suspend fun updateOrCreateDraft(
        owner: String,
        repository: String,
        riScId: String,
        defaultBranch: String,
        fileContent: String,
        requiresNewApproval: Boolean,
        gitHubAccessToken: GithubAccessToken,
        userInfo: UserInfo,
    ): RiScApprovalPRStatus {
        val filePath = draftFilePath(repository, riScId)
        Files.createDirectories(filePath.parent)
        filePath.writeText(fileContent)

        val prDir = prDir(repository, riScId)
        val prExists = prDir.isDirectory()
        val isPublished = publishedFilePath(repository, riScId).isRegularFile()

        return when {
            !requiresNewApproval && !prExists && isPublished -> {
                Files.createDirectories(prDir)
                RiScApprovalPRStatus(pullRequest = localPrObject(repository, riScId), hasClosedPr = false)
            }
            requiresNewApproval && prExists -> {
                prDir.toFile().deleteRecursively()
                RiScApprovalPRStatus(pullRequest = null, hasClosedPr = true)
            }
            else -> RiScApprovalPRStatus(pullRequest = null, hasClosedPr = false)
        }
    }

    override suspend fun deleteRiSc(
        owner: String,
        repository: String,
        accessToken: String,
        riScId: String,
    ): DeleteRiScResultDTO {
        val publishedFile = publishedFilePath(repository, riScId)
        val branchDir = branchDir(repository, riScId)
        val draftFile = draftFilePath(repository, riScId)

        return if (!publishedFile.exists()) {
            // Never published — delete the draft branch and any open PR
            branchDir.toFile().deleteRecursively()
            prDir(repository, riScId).toFile().deleteRecursively()
            DeleteRiScResultDTO(
                riScId = riScId,
                status = ProcessingStatus.DeletedRiSc,
                statusMessage = "Risk scorecard was deleted - no approval required as it was never published",
            )
        } else {
            // Was published — stage deletion by removing just the draft file
            Files.createDirectories(branchDir)
            if (draftFile.exists()) draftFile.toFile().delete()
            DeleteRiScResultDTO(
                riScId = riScId,
                status = ProcessingStatus.DeletedRiScRequiresApproval,
                statusMessage = "Risk scorecard was staged for deletion - the deletion requires approval",
            )
        }
    }

    override suspend fun createPullRequestForRiSc(
        owner: String,
        repository: String,
        riScId: String,
        requiresNewApproval: Boolean,
        gitHubAccessToken: String,
        userInfo: UserInfo,
    ): GithubPullRequestObject {
        Files.createDirectories(prDir(repository, riScId))
        return localPrObject(repository, riScId)
    }

    private fun localPrObject(
        repository: String,
        riScId: String,
    ): GithubPullRequestObject =
        GithubPullRequestObject(
            url = prDir(repository, riScId).toUri().toString(),
            title = "Updated risk scorecard",
            createdAt = OffsetDateTime.now(),
            head = GithubPullRequestBranch(ref = riScId),
            base = GithubPullRequestBranch(ref = "default"),
            number = 0,
        )
}
