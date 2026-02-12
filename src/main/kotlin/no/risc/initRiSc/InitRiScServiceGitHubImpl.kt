package no.risc.initRiSc

import kotlinx.serialization.json.Json
import no.risc.exception.exceptions.FetchException
import no.risc.exception.exceptions.RiScNotValidOnFetchException
import no.risc.github.GithubConnector
import no.risc.github.models.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.initRiSc.model.InitRiScDescriptorConfig
import no.risc.initRiSc.model.RiScTypeDescriptor
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc5X
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.utils.yamlToJson
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class InitRiScServiceGitHubImpl(
    @Value("\${initRiSc.repoName}") val initRiScRepoName: String,
    @Value("\${initRiSc.repoOwner}") val initRiScRepoOwner: String,
    private val githubConnector: GithubConnector,
) : InitRiScService {
    override suspend fun getInitRiScDescriptors(accessTokens: AccessTokens): List<RiScTypeDescriptor> {
        val initRiScDescriptorConfigs = getInitRiScDescriptorConfigs(accessTokens)
        return initRiScDescriptorConfigs.map {
            val initRiSc = getInitRiScFromGitHub(it.id, accessTokens)
            RiScTypeDescriptor(
                it.id,
                it.listName,
                it.listDescription,
                initRiSc.title,
                initRiSc.scope,
                initRiSc.getNumberOfScenarios(),
                initRiSc.getNumberOfActions(),
                it.preferredBackstageComponentType,
                it.priorityIndex,
            )
        }
    }

    /**
     * Gets an initial risc from GitHub, ready to be used by teams.
     *
     * @param initialContent A JSON serialized RiSc to base the default RiSc on. Must include the `title` and `scope`
     *                    fields. These are the only fields used from initialContent.
     *
     * @param initRiScId ID of default RiSc to generate the RiSc from.
     */
    override suspend fun getInitRiSc(
        initRiScId: String,
        initialContent: String,
        accessTokens: AccessTokens,
    ): String {
        val parsedInitialContent = RiSc.fromContent(initialContent)

        if (parsedInitialContent !is RiSc5X) {
            throw Exception(
                "Parsed initial content is not of schema version 5.x. The RiSc was of type ${parsedInitialContent::class.simpleName}",
            )
        }

        val initRiSc = getCleanedInitRiSc(initRiScId, accessTokens)
        val initRiScWithInitialContent =
            initRiSc.copy(
                title = parsedInitialContent.title,
                scope = parsedInitialContent.scope,
            )
        return Json.encodeToString(initRiScWithInitialContent)
    }

    /**
     * Returns an initial RiSc without  lastUpdated and lastUpdatedBy.
     * In addition, all action statuses of the returned RiSc are set to "NOT OK".
     */
    private suspend fun getCleanedInitRiSc(
        id: String,
        accessTokens: AccessTokens,
    ): RiSc5X {
        val fetchedRawInitRiSc = getInitRiScFromGitHub(id, accessTokens)
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

    private suspend fun getInitRiScFromGitHub(
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
            throw FetchException(
                "Failed to fetch initial RiSc from GitHub. Fetch status was ${fetchedInitRiSc.status} and data was " +
                    "${if (fetchedInitRiSc.data == null) "null" else fetchedInitRiSc.data.substring(0, 20)}...",
                ProcessingStatus.FailedToFetchInitRiScFromGitHub,
            )
        }
        val fetchedInitRiScJson = yamlToJson(fetchedInitRiSc.data)
        val initRiSc = RiSc.fromContent(fetchedInitRiScJson)

        if (initRiSc !is RiSc5X) {
            throw RiScNotValidOnFetchException(
                "Fetched initial RiSc is not of schema version 5.x. The RiSc was of type ${initRiSc::class.simpleName}",
                id,
            )
        }

        return initRiSc
    }

    private suspend fun getInitRiScDescriptorConfigs(accessTokens: AccessTokens): List<InitRiScDescriptorConfig> {
        val fetchedInitRiScDescriptors = githubConnector.fetchInitRiScDescriptors(accessTokens.githubAccessToken)
        if (fetchedInitRiScDescriptors.status != GithubStatus.Success || fetchedInitRiScDescriptors.data == null) {
            throw FetchException(
                "Failed to fetch initial RiSc from GitHub. Fetch status was ${fetchedInitRiScDescriptors.status} and data was " +
                    "${if (fetchedInitRiScDescriptors.data == null) "null" else fetchedInitRiScDescriptors.data.substring(0, 20)}...",
                ProcessingStatus.FailedToFetchInitRiScConfigFromGitHub,
            )
        }

        val initRiscDescriptorConfigs = Json.decodeFromString<List<InitRiScDescriptorConfig>>(fetchedInitRiScDescriptors.data)
        return initRiscDescriptorConfigs
    }
}
