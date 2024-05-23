package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@Service
class GithubTokenService(
    @Value("\${githubAppIdentifier.appId}") private val appId: Int,
    @Value("\${githubAppIdentifier.installationId}") private val installationId: Int,
) {

    private val installationToken: AtomicReference<GithubAccessTokenBody> = AtomicReference()

    private fun generateJWT(privateKey: String): String? {
        val jwk = JWK.parseFromPEMEncodedObjects(privateKey)
        val signer = RSASSASigner(jwk.toRSAKey())
        val jwtClaimSet =
            JWTClaimsSet.Builder()
                .issuer(appId.toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(10 * 60)))
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .build()

        val signedJwt =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwk.keyID).build(), jwtClaimSet)
        signedJwt.sign(signer)
        val token = signedJwt.serialize()
        return token
    }

    fun getInstallationToken(privateKey: String): String {
        if (installationToken.get() != null && installationToken.get().isNotExpired()) {
            return installationToken.get().token
        }
        installationToken.set(fetchGitHubInstallationToken(privateKey))
        return installationToken.get().token
    }

    private fun fetchGitHubInstallationToken(privateKey: String): GithubAccessTokenBody {
        return RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${generateJWT(privateKey)}")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString())
            .build()
            .post()
            .uri { uriBuilder ->
                uriBuilder.path("/app/installations/$installationId/access_tokens").build()
            }
            .retrieve()
            .body(GithubAccessTokenBody::class.java)!!
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubAccessTokenBody(
    val token: String,
    @JsonProperty("expires_at") val expiresAt: OffsetDateTime,
)

private fun GithubAccessTokenBody.isNotExpired(): Boolean {
    return expiresAt.isAfter(OffsetDateTime.now())
}