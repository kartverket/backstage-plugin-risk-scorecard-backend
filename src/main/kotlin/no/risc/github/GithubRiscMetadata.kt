package no.risc.github
import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubStatus
import no.risc.risc.models.RiScStatus

data class RiScGithubMetadata(
    val id: String,
    val isStoredInMain: Boolean,
    val hasBranch: Boolean,
    val hasOpenPR: Boolean,
    val prUrl: String?,
)

data class RiScMainAndBranchContent(
    val mainContent: GithubContentResponse,
    val branchContent: GithubContentResponse,
)

data class RiScMainAndBranchContentWithLastPublishedInfo(
    val contents: RiScMainAndBranchContent,
    val lastPublished: no.risc.risc.models.LastPublished? = null,
)

fun chooseRiScContentFromStatus(
    status: RiScStatus,
    branchRiScContent: GithubContentResponse,
    mainRiscContent: GithubContentResponse,
): GithubContentResponse =
    when (status) {
        RiScStatus.SentForApproval, RiScStatus.Draft -> branchRiScContent
        RiScStatus.Published, RiScStatus.DeletionDraft, RiScStatus.DeletionSentForApproval -> mainRiscContent
        else -> GithubContentResponse(null, GithubStatus.ContentIsEmpty)
    }

fun getRiScStatus(
    riScGithubMetadata: RiScGithubMetadata,
    mainRiscContent: GithubContentResponse,
    branchRiscContent: GithubContentResponse,
): RiScStatus {
    val state =
        RiscGithubBranchesState(
            getMainState(riScGithubMetadata),
            getBranchState(riScGithubMetadata, branchRiscContent, mainRiscContent),
            getPRState(riScGithubMetadata),
        )
    return fromRiScGithubStateToStatus(state)
}

private enum class RiscBranchState {
    DOES_NOT_EXIST,
    EXISTS_WITH_SAME_FILE,
    EXISTS_WITHOUT_FILE,
    EXISTS_WITH_DIFFERENT_FILE,
}

private enum class MainBranchState {
    HAS_FILE,
    NO_FILE,
}

private enum class PRState {
    OPEN,
    NONE,
}

private data class RiscGithubBranchesState(
    val mainBranchState: MainBranchState,
    val riscBranchState: RiscBranchState,
    val prState: PRState,
)

private fun fromRiScGithubStateToStatus(state: RiscGithubBranchesState): RiScStatus {
    if (state.mainBranchState == MainBranchState.HAS_FILE) {
        return when (state.riscBranchState) {
            RiscBranchState.DOES_NOT_EXIST -> RiScStatus.Published
            RiscBranchState.EXISTS_WITH_SAME_FILE -> RiScStatus.Published
            RiscBranchState.EXISTS_WITHOUT_FILE ->
                if (state.prState == PRState.OPEN) RiScStatus.DeletionSentForApproval else RiScStatus.DeletionDraft
            RiscBranchState.EXISTS_WITH_DIFFERENT_FILE ->
                if (state.prState == PRState.OPEN) RiScStatus.SentForApproval else RiScStatus.Draft
        }
    } else {
        return when (state.riscBranchState) {
            RiscBranchState.DOES_NOT_EXIST -> RiScStatus.Deleted
            RiscBranchState.EXISTS_WITH_SAME_FILE -> RiScStatus.Deleted
            RiscBranchState.EXISTS_WITHOUT_FILE -> RiScStatus.Deleted
            RiscBranchState.EXISTS_WITH_DIFFERENT_FILE ->
                if (state.prState == PRState.OPEN) {
                    RiScStatus.SentForApproval
                } else {
                    RiScStatus.Draft
                }
        }
    }
}

private fun getMainState(riScGithubMetadata: RiScGithubMetadata): MainBranchState {
    if (riScGithubMetadata.isStoredInMain) return MainBranchState.HAS_FILE
    return MainBranchState.NO_FILE
}

private fun getBranchState(
    riScGithubMetadata: RiScGithubMetadata,
    branchRiscContent: GithubContentResponse,
    mainRiscContent: GithubContentResponse,
): RiscBranchState {
    if (!riScGithubMetadata.hasBranch) return RiscBranchState.DOES_NOT_EXIST
    if (branchRiscContent.status != GithubStatus.Success) return RiscBranchState.EXISTS_WITHOUT_FILE

    if (mainRiscContent.status == GithubStatus.Success) {
        // Risc exists in both branches
        if (mainRiscContent.data == branchRiscContent.data) return RiscBranchState.EXISTS_WITH_SAME_FILE
        return RiscBranchState.EXISTS_WITH_DIFFERENT_FILE
    }
    return RiscBranchState.EXISTS_WITH_DIFFERENT_FILE
}

private fun getPRState(riScGithubMetadata: RiScGithubMetadata): PRState {
    if (riScGithubMetadata.hasOpenPR) return PRState.OPEN
    return PRState.NONE
}
