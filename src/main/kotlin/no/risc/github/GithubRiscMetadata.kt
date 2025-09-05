package no.risc.github
import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubStatus
import no.risc.risc.models.RiScStatus

enum class RiscBranchState {
    DOES_NOT_EXIST,
    EXISTS_WITH_SAME_FILE,
    EXISTS_WITHOUT_FILE,
    EXISTS_WITH_DIFFERENT_FILE,
}

enum class MainBranchState {
    HAS_FILE,
    NO_FILE,
}

enum class PRState {
    OPEN,
    NONE
}

data class RiscState(var mainBranchState: MainBranchState, var riscBranchState: RiscBranchState, var prState: PRState)

data class RiScMetadata(
    val id: String,
    val isStoredInMain: Boolean,
    val hasBranch: Boolean,
    val hasOpenPR: Boolean,
    val prUrl: String?
)

data class RiScMainAndBranchContent(val mainContent: GithubContentResponse, val branchContent: GithubContentResponse)

fun fromRiscStateToStatus(state: RiscState): RiScStatus {
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
                if (state.prState == PRState.OPEN)
                    RiScStatus.SentForApproval
                else
                    RiScStatus.Draft
        }
    }
}

fun chooseRiScContentFromStatus(
    status: RiScStatus,
    branchRiScContent: GithubContentResponse,
    mainRiscContent: GithubContentResponse): GithubContentResponse {
    return when (status) {
        RiScStatus.SentForApproval, RiScStatus.Draft -> branchRiScContent
        RiScStatus.Published, RiScStatus.DeletionDraft, RiScStatus.DeletionSentForApproval -> mainRiscContent
        else -> GithubContentResponse(null, GithubStatus.ContentIsEmpty)
    }
}

fun getMainState(riScMetadata: RiScMetadata): MainBranchState {
    if (riScMetadata.isStoredInMain) return MainBranchState.HAS_FILE
    return MainBranchState.NO_FILE
}

fun getBranchState(
    riScMetadata: RiScMetadata,
    branchRiscContent: GithubContentResponse,
    mainRiscContent: GithubContentResponse): RiscBranchState {

    if (!riScMetadata.hasBranch) return RiscBranchState.DOES_NOT_EXIST
    if (branchRiscContent.status != GithubStatus.Success) return RiscBranchState.EXISTS_WITHOUT_FILE

    if (mainRiscContent.status == GithubStatus.Success) {
        // Risc exists in both branches
        if (mainRiscContent.data == branchRiscContent.data) return RiscBranchState.EXISTS_WITH_SAME_FILE
        return RiscBranchState.EXISTS_WITH_DIFFERENT_FILE
    }
    return RiscBranchState.EXISTS_WITH_DIFFERENT_FILE

}

fun getPRState(riScMetadata: RiScMetadata): PRState {
    if (riScMetadata.hasOpenPR) return PRState.OPEN
    return PRState.NONE
}

fun getRiScStatus(
    riScMetadata: RiScMetadata,
    mainRiscContent: GithubContentResponse,
    branchRiscContent: GithubContentResponse
): RiScStatus {
    val state = RiscState(
        getMainState(riScMetadata),
        getBranchState(riScMetadata, branchRiscContent, mainRiscContent),
        getPRState(riScMetadata)
    )
    return fromRiscStateToStatus(state)
}