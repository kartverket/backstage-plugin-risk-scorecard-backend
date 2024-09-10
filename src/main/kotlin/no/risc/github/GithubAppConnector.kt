package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.risc.infra.connector.WebClientConnector
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date

class GithubAccessToken(
    val value: String,
)

@Component
class GithubAppConnector(
    @Value("\${githubAppIdentifier.appId}") private val appId: Int,
    @Value("\${githubAppIdentifier.installationId}") private val installationId: Int,
    @Value("\${githubAppIdentifier.privateKey}") private val base64EncodedPrivateKey: String,
    private val githubHelper: GithubHelper,
) :
    WebClientConnector("https://api.github.com/app") {
    private val logger: Logger = getLogger(GithubAppConnector::class.java)
    private val githubAppPrivateKey = Base64.getDecoder().decode(base64EncodedPrivateKey)

    internal fun getAccessTokenFromApp(repositoryName: String): GithubAccessToken {
        return GithubAccessToken(
            getGithubAppAccessToken(
                jwt = generateJWT(),
                repositoryName = repositoryName,
            ).token,
        )
    }

    private fun getGithubAppAccessToken(
        jwt: GithubAppSignedJwt,
        repositoryName: String,
    ): GithubAccessTokenBody =
        try {
            webClient
                .post()
                .uri(githubHelper.uriToGetAccessTokenFromInstallation(installationId.toString()))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.value}")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(githubHelper.bodyToGetAccessToken(repositoryName).toContentBody())
                .retrieve()
                .bodyToMono<GithubAccessTokenBody>()
                .block() ?: throw Exception("Access token is null.")
        } catch (e: Exception) {
            logger.error("Could not create access token with error message: ${e.message}.")
            throw Exception("Could not create access token with error message: ${e.message}.")
        }

    data class GithubAppSignedJwt(
        val value: String?,
    )

    fun generateJWT(): GithubAppSignedJwt {
        val jwk = JWK.parseFromPEMEncodedObjects(String(githubAppPrivateKey))
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
        return GithubAppSignedJwt(signedJwt.serialize())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GithubAccessTokenBody(
        val token: String,
        @JsonProperty("expires_at") val expiresAt: OffsetDateTime,
    )
}
