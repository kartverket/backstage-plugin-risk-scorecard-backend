package no.risc.github.models

data class BranchInfo(
    val exists: Boolean,
    val latestSha: String = "",
) {
    companion object {
        val BRANCH_DOES_NOT_EXIST = BranchInfo(exists = false)
    }
}
