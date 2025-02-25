package no.risc.github

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import no.risc.config.GitHubAppConfig
import no.risc.github.models.GitHubAccessTokenResponse
import no.risc.github.models.isNotExpired
import no.risc.infra.connector.models.GithubAccessToken
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

@Service
class GitHubAppService(
    val gitHubAppConfig: GitHubAppConfig,
    val environment: Environment,
) {
    private val installationToken: AtomicReference<GitHubAccessTokenResponse> = AtomicReference()

    private fun generateJWT(): String? {
        val jwk = JWK.parseFromPEMEncodedObjects(String(Base64.getDecoder().decode(gitHubAppConfig.privateKey)))
        val signer = RSASSASigner(jwk.toRSAKey())
        val jwtClaimSet =
            JWTClaimsSet
                .Builder()
                .issuer(gitHubAppConfig.id)
                .expirationTime(Date.from(Instant.now().plusSeconds(10 * 60)))
                .issueTime(Date.from(Instant.now().minusSeconds(60)))
                .build()

        val signedJwt =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(jwk.keyID).build(), jwtClaimSet)
        signedJwt.sign(signer)
        return signedJwt.serialize()
    }

    private fun getInstallationToken(): GithubAccessToken {
        if (installationToken.get() != null && installationToken.get().isNotExpired()) {
            return GithubAccessToken(installationToken.get().token)
        }
        installationToken.set(fetchGitHubInstallationToken())
        return GithubAccessToken(installationToken.get().token)
    }

    fun getGitHubAccessToken(tokenFromHeader: String?): GithubAccessToken =
        tokenFromHeader?.let { GithubAccessToken(it) }
            ?: if (environment.activeProfiles.contains("local")) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A GitHub personal access token MUST be provided when running locally.",
                )
            } else {
                getInstallationToken()
            }

    private fun fetchGitHubInstallationToken(): GitHubAccessTokenResponse =
        RestClient
            .builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${generateJWT()}")
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString())
            .build()
            .post()
            .uri { uriBuilder ->
                uriBuilder.path("/app/installations/${gitHubAppConfig.installationId}/access_tokens").build()
            }.retrieve()
            .body(GitHubAccessTokenResponse::class.java)!!
}
