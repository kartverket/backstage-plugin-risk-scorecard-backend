package no.risc.google

import no.risc.infra.connector.models.GCPAccessToken
import no.risc.sops.model.GcpCryptoKeyObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/google")
class GoogleControllerIntegration(
    private val googleServiceIntegration: GoogleServiceIntegration,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(GoogleControllerIntegration::class.java)
    }

    @GetMapping("/gcpCryptoKeys")
    suspend fun getGcpCryptoKeys(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
    ): List<GcpCryptoKeyObject> {
        LOGGER.info("Fetching GCP crypto keys")
        return googleServiceIntegration.getGcpCryptoKeys(GCPAccessToken(gcpAccessToken))
    }
}
