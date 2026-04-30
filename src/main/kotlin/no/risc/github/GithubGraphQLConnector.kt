package no.risc.github

import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono

/**
 * A connector for performing GitHub GraphQL API mutations that are not available through the REST API.
 * Currently supports converting pull requests to draft and marking them ready for review.
 *
 * @see <a href="https://docs.github.com/en/graphql">GitHub GraphQL API documentation</a>
 */
@Component
class GithubGraphQLConnector {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(GithubGraphQLConnector::class.java)
        private const val GRAPHQL_URL = "https://api.github.com/graphql"
    }

    private val webClient: WebClient =
        WebClient
            .builder()
            .baseUrl(GRAPHQL_URL)
            .build()

    /**
     * Converts a pull request to a draft using the GitHub GraphQL API.
     *
     * @param pullRequestNodeId The global node ID of the pull request (from the REST API's `node_id` field).
     * @param accessToken The GitHub access token for authorization.
     * @return `true` if the mutation succeeded, `false` otherwise.
     * @see <a href="https://docs.github.com/en/graphql/reference/mutations#convertpullrequesttodraft">
     *      convertPullRequestToDraft mutation</a>
     */
    suspend fun convertPullRequestToDraft(
        pullRequestNodeId: String,
        accessToken: String,
    ): Boolean =
        executeMutation(
            mutationName = "convertPullRequestToDraft",
            query =
                """
                mutation {
                    convertPullRequestToDraft(input: { pullRequestId: "$pullRequestNodeId" }) {
                        pullRequest { id isDraft }
                    }
                }
                """.trimIndent(),
            accessToken = accessToken,
        )

    /**
     * Marks a draft pull request as ready for review using the GitHub GraphQL API.
     *
     * @param pullRequestNodeId The global node ID of the pull request (from the REST API's `node_id` field).
     * @param accessToken The GitHub access token for authorization.
     * @return `true` if the mutation succeeded, `false` otherwise.
     * @see <a href="https://docs.github.com/en/graphql/reference/mutations#markpullrequestreadyforreview">
     *      markPullRequestReadyForReview mutation</a>
     */
    suspend fun markPullRequestReadyForReview(
        pullRequestNodeId: String,
        accessToken: String,
    ): Boolean =
        executeMutation(
            mutationName = "markPullRequestReadyForReview",
            query =
                """
                mutation {
                    markPullRequestReadyForReview(input: { pullRequestId: "$pullRequestNodeId" }) {
                        pullRequest { id isDraft }
                    }
                }
                """.trimIndent(),
            accessToken = accessToken,
        )

    /**
     * Executes a GraphQL mutation against the GitHub API.
     *
     * @param mutationName A human-readable name for logging.
     * @param query The GraphQL query string.
     * @param accessToken The GitHub access token for authorization.
     * @return `true` if the mutation succeeded (HTTP 2xx and no GraphQL errors), `false` otherwise.
     */
    private suspend fun executeMutation(
        mutationName: String,
        query: String,
        accessToken: String,
    ): Boolean =
        try {
            val requestBody = mapOf("query" to query)

            val response =
                webClient
                    .post()
                    .header("Authorization", "bearer $accessToken")
                    .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Mono.just(requestBody), Map::class.java)
                    .retrieve()
                    .toEntity<String>()
                    .awaitSingle()

            if (!response.statusCode.is2xxSuccessful) {
                LOGGER.error(
                    "GitHub GraphQL {} failed with HTTP {}: {}",
                    mutationName,
                    response.statusCode,
                    response.body,
                )
                return false
            }

            val body = response.body ?: ""
            if (body.contains("\"errors\"")) {
                LOGGER.error("GitHub GraphQL {} returned errors: {}", mutationName, body)
                return false
            }

            LOGGER.debug("GitHub GraphQL {} succeeded", mutationName)
            true
        } catch (e: Exception) {
            LOGGER.error("GitHub GraphQL {} failed with exception: {}", mutationName, e.message, e)
            false
        }
}
