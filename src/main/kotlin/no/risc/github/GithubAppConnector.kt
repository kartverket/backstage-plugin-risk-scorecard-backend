package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import no.risc.infra.connector.GcpClientConnector
import no.risc.infra.connector.WebClientConnector
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

class GithubAccessToken(
    val value: String,
)

@Component
class GithubAppConnector(
    @Value("\${githubAppIdentifier.appId}") private val appId: Int,
    @Value("\${githubAppIdentifier.installationId}") private val installationId: Int,
    @Value("\${githubAppIdentifier.privateKeySecretName}") private val privateKeySecretName: String,
    private val githubHelper: GithubHelper,
) :
    WebClientConnector("https://api.github.com/app") {
    private val logger: Logger = getLogger(GithubAppConnector::class.java)
    private val gcpClientConnector = GcpClientConnector()

    internal fun getAccessTokenFromApp(repositoryName: String): GithubAccessToken {
        val jwt = getGithubAppSignedJWT()
        return getGithubAppAccessToken(jwt, repositoryName = repositoryName)
    }

    private fun getGithubAppSignedJWT(): GithubAppSignedJwt =
        GithubAppSignedJwt(
            PemUtils.getSignedJWT(
                privateKey =
                    gcpClientConnector.getSecretValue(privateKeySecretName)?.toByteArray()
                        ?: throw Exception("Kunne ikke hente github app private key"),
                appId = appId,
            ),
        )

    private fun getGithubAppAccessToken(
        jwt: GithubAppSignedJwt,
        repositoryName: String,
    ): GithubAccessToken {
        val accessTokenBody: GithubAccessTokenBody =
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

        return GithubAccessToken(accessTokenBody.token)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GithubAccessTokenBody(
        val token: String,
    )

    private data class GithubAppSignedJwt(
        val value: String?,
    )
}

object PemUtils {
    class PEM_CONVERSION_EXCEPTION : Exception()

    fun getSignedJWT(
        privateKey: ByteArray,
        appId: Int,
    ): String {
        val pemContent = String(privateKey, StandardCharsets.UTF_8)
        val privateKey = readPrivateKey(pemContent)

        return Jwts
            .builder()
            .setIssuedAt(Date.from(Instant.now().minusSeconds(60)))
            .setExpiration(Date.from(Instant.now().plusSeconds(600)))
            .setIssuer(appId.toString())
            .setHeader(
                mapOf(
                    "typ" to "JWT",
                    "alg" to "RS256",
                ),
            )
            .signWith(privateKey, SignatureAlgorithm.RS256)
            .compact()
    }

    private fun convertToPkcs8(pemKey: String): String =
        ProcessBuilder("openssl", "pkcs8", "-topk8", "-inform", "PEM", "-outform", "PEM", "-nocrypt").start()
            .run {
                outputStream.buffered().also { it.write(pemKey.toByteArray()) }.close()
                val result = BufferedReader(InputStreamReader(inputStream)).readText()
                when (waitFor()) {
                    0 -> result
                    else -> throw PEM_CONVERSION_EXCEPTION()
                }
            }

    private fun readPrivateKey(pem: String): PrivateKey {
        val pkcs8Key = if (pem.isPKCS1()) convertToPkcs8(pem) else pem

        val decodedKey =
            pkcs8Key
                .stripToOnlyPrivateKey()
                .base64Decode()

        val keySpec = PKCS8EncodedKeySpec(decodedKey)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun String.isPKCS1(): Boolean = this.contains("RSA")

    private fun String.stripToOnlyPrivateKey(): String =
        this
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")

    private fun String.base64Decode(): ByteArray = Base64.getDecoder().decode(this)
}
