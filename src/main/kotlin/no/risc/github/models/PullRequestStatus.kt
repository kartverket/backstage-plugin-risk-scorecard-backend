package no.risc.github.models

enum class PullRequestStatus {
    PullRequestExist,
    PullRequestDoesNotExist,
    ;

    fun exists(): Boolean = this === PullRequestExist
}

enum class PullRequestActionStatus {
    Closed,
    Opened,
    Error,
    ;

    fun closedSuccessfully(): Boolean = this === Closed
}
