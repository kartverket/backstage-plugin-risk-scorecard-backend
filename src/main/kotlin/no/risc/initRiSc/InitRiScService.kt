package no.risc.initRiSc

import kotlinx.serialization.json.Json
import no.risc.github.GithubConnector
import no.risc.github.models.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.initRiSc.model.InitRiScDescriptorConfig
import no.risc.initRiSc.model.RiScTypeDescriptor
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc5X
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.utils.yamlToJson
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class InitRiScService(
    @Value("\${initRiSc.repoName}") val initRiScRepoName: String,
    @Value("\${initRiSc.repoOwner}") val initRiScRepoOwner: String,
    private val githubConnector: GithubConnector,
) {
    suspend fun getInitRiScDescriptors(accessTokens: AccessTokens): List<RiScTypeDescriptor> {
        val initRiScDescriptorConfigs = getInitRiScDescriptorConfigs(accessTokens)
        return initRiScDescriptorConfigs.map {
            val initRiSc = fetchRawInitRiSc(it.id, accessTokens)
            RiScTypeDescriptor(
                it.id,
                it.listName,
                it.listDescription,
                initRiSc.title,
                initRiSc.scope,
                getNumberOfScenarios(initRiSc),
                getNumberOfActions(initRiSc),
                it.preferredBackstageComponentType,
                it.priorityIndex,
            )
        }
    }

    suspend fun getInitRiScDescriptorConfigs(accessTokens: AccessTokens): List<InitRiScDescriptorConfig> {
        val fetchedInitRiScDescriptors = githubConnector.fetchInitRiScDescriptors(accessTokens.githubAccessToken)
        if (fetchedInitRiScDescriptors.status != GithubStatus.Success || fetchedInitRiScDescriptors.data == null) {
            throw Exception("Failed to fetch initial risc descriptors from GitHub.")
        }

        val initRiscDescriptorConfigs = Json.decodeFromString<List<InitRiScDescriptorConfig>>(fetchedInitRiScDescriptors.data)
        return initRiscDescriptorConfigs
    }

    /**
     * Gets an initial risc from GitHub, ready to be used by teams.
     *
     * @param initialContent A JSON serialized RiSc to base the default RiSc on. Must include the `title` and `scope`
     *                    fields. These are the only fields used from initialContent.
     *
     * @param initRiScId ID of default RiSc to generate the RiSc from.
     */

    suspend fun getInitRiSc(
        initRiScId: String,
        accessTokens: AccessTokens,
        initialContent: String?,
    ): RiSc5X {
        if (initialContent == null) {
            return getCleanedInitRiSc(initRiScId, accessTokens)
        }

        val parsedInitialContent = RiSc.fromContent(initialContent)

        if (parsedInitialContent !is RiSc5X) {
            throw Exception("Initial RiSc content is not a valid RiSc of schema version 5.x")
        }

        val initRiSc = getCleanedInitRiSc(initRiScId, accessTokens)
        return initRiSc.copy(
            title = parsedInitialContent.title,
            scope = parsedInitialContent.scope,
        )
    }

    private suspend fun getCleanedInitRiSc(
        id: String,
        accessTokens: AccessTokens,
    ): RiSc5X {
        val fetchedRawInitRiSc = fetchRawInitRiSc(id, accessTokens)
        val newScenarios =
            fetchedRawInitRiSc.scenarios.map { scenario ->

                val newActions =
                    scenario.actions.map { action ->
                        action.copy(
                            status = RiScScenarioActionStatus.NOT_OK,
                            lastUpdated = null,
                            lastUpdatedBy = null,
                        )
                    }

                scenario.copy(
                    actions = newActions,
                )
            }
        return fetchedRawInitRiSc.copy(
            scenarios = newScenarios,
        )
    }

    private suspend fun fetchRawInitRiSc(
        id: String,
        accessTokens: AccessTokens,
    ): RiSc5X {
        val fetchedInitRiSc =
            githubConnector.fetchPublishedRiSc(
                initRiScRepoOwner,
                initRiScRepoName,
                id,
                accessTokens.githubAccessToken.value,
            )

        if (fetchedInitRiSc.status != GithubStatus.Success || fetchedInitRiSc.data == null) {
            throw Exception("Failed to fetch initial risc descriptors from GitHub.")
        }
        val fetchedInitRiScJson = yamlToJson(fetchedInitRiSc.data)
        val initRiSc = RiSc.fromContent(fetchedInitRiScJson)

        if (initRiSc !is RiSc5X) {
            throw Exception("RiSc is not a valid RiSc of schema version 5.x")
        }

        return initRiSc
    }

    private fun getNumberOfScenarios(riSc: RiSc5X) = riSc.scenarios.size

    private fun getNumberOfActions(riSc: RiSc5X) = riSc.scenarios.sumOf { it.actions.size }
}
