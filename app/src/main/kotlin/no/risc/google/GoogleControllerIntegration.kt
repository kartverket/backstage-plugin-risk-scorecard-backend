package no.risc.google

import io.swagger.v3.oas.annotations.tags.Tag
import no.risc.google.model.GcpCryptoKeyObject
import no.risc.infra.connector.models.GCPAccessToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/google")
@Tag(name = "google", description = "GCP integration endpoints")
class GoogleControllerIntegration(
    private val googleServiceIntegration: GoogleServiceIntegration,
) {
    @GetMapping("/gcpCryptoKeys")
    suspend fun getGcpCryptoKeys(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
    ): List<GcpCryptoKeyObject> = googleServiceIntegration.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken(gcpAccessToken))
}
