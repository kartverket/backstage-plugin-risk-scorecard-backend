package no.risc.sops

import no.risc.config.SopsServiceConfig
import no.risc.exception.exceptions.GcpProjectIdFetchException
import no.risc.exception.exceptions.SopsConfigFetchException
import no.risc.github.GithubConnector
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.GcpProjectId
import no.risc.sops.model.GetSopsConfigResponse
import no.risc.sops.model.SopsConfig
import no.risc.utils.YamlUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class SopsService(
    private val githubConnector: GithubConnector,
    private val sopsServiceConfig: SopsServiceConfig,
    private val googleApiConnector: GoogleApiConnector,
) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(SopsService::class.java)
    }

    fun getSopsConfig(
        repositoryOwner: String,
        repositoryName: String,
        accesssTokens: AccessTokens,
    ): GetSopsConfigResponse {
        val gcpProjectIds =
            googleApiConnector.fetchProjectIds(accesssTokens.gcpAccessToken)
                ?: throw GcpProjectIdFetchException(
                    message = "Fetch of sops config responded with 200 OK but file contents was null",
                    GetSopsConfigResponse(
                        status = ProcessingStatus.FailedToFetchGcpProjectIds,
                        gcpProjectId = GcpProjectId(""),
                        publicAgeKeys = emptyList(),
                        gcpProjectIds = emptyList(),
                    ),
                )

        val sopsConfig =
            try {
                YamlUtils.to<SopsConfig>(
                    githubConnector
                        .fetchSopsConfigFromDefaultBranch(
                            repositoryOwner,
                            repositoryName,
                            accesssTokens.githubAccessToken,
                        ).data
                        ?: throw throw SopsConfigFetchException(
                            message = "Fetch of sops config responded with 200 OK but file contents was null",
                            riScId = "",
                            responseMessage = "Could not fetch SOPS config",
                        ),
                )
            } catch (e: WebClientResponseException.NotFound) {
                return GetSopsConfigResponse(
                    status = ProcessingStatus.NoSopsConfigFound,
                    gcpProjectId = GcpProjectId(""),
                    publicAgeKeys = emptyList(),
                    gcpProjectIds = gcpProjectIds,
                )
            }

        return GetSopsConfigResponse(
            status = ProcessingStatus.FetchedSopsConfig,
            gcpProjectId = sopsConfig.getGcpProjectId(),
            publicAgeKeys = sopsConfig.getDeveloperPublicKeys(sopsServiceConfig.backendPublicKey),
            gcpProjectIds = gcpProjectIds,
        )
    }
}
