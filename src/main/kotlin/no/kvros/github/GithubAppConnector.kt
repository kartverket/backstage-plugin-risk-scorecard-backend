package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import no.kvros.infra.connector.GcpClientConnector
import no.kvros.infra.connector.WebClientConnector
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
import java.util.*

class GithubAppSignedJwt(
    val value: String?
)

class GithubAccessToken(
    val value: String
)

class GithubAppIdentifier(
    val appId: Int,
    val installationId: Int
)

@Component
class GithubAppConnector(
    @Value("\${githubAppIdentifier.appId}") appId: Int,
    @Value("\${githubAppIdentifier.installationId}") installationId: Int,
    @Value("\${githubAppIdentifier.privateKeySecretName}") val privateKeySecretName: String,
) :
    WebClientConnector("https://api.github.com/app") {

    private val gcpClientConnector = GcpClientConnector()
    private val appIdentifier = GithubAppIdentifier(appId, installationId)

    internal fun getAccessTokenFromApp(repositoryName: String): GithubAccessToken {
        val jwt = getGithubAppSignedJWT()
        return getGithubAppAccessToken(jwt, repositoryName = repositoryName)
    }

    private fun getGithubAppSignedJWT(): GithubAppSignedJwt = GithubAppSignedJwt(
        PemUtils.getSignedJWT(
            privateKey = gcpClientConnector.getSecretValue(privateKeySecretName)?.toByteArray()
                ?: throw Exception("Kunne ikke hente github app private key"),
            appId = appIdentifier.appId
        )
    )

    private fun getGithubAppAccessToken(jwt: GithubAppSignedJwt, repositoryName: String): GithubAccessToken {
        val accessTokenBody: GithubAccessTokenBody =
            try {
                webClient
                    .post()
                    .uri(GithubHelper.uriToGetAccessTokenFromInstallation(appIdentifier.installationId.toString()))
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.value}")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono<GithubAccessTokenBody>()
                    .block() ?: throw Exception("Access token is null.")
            } catch (e: Exception) {
                println(e.stackTrace)
                throw Exception("Could not create access token with error message: ${e.message}.")
            }

        return GithubAccessToken(accessTokenBody.token)
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GithubAccessTokenBody(
        val token: String,
    )
}

object PemUtils {
    class PEM_CONVERSION_EXCEPTION() : Exception()

    fun getSignedJWT(privateKey: ByteArray, appId: Int): String {
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
                    "alg" to "RS256"
                )
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

        val decodedKey = pkcs8Key
            .stripToOnlyPrivateKey()
            .base64Decode()

        val keySpec = PKCS8EncodedKeySpec(decodedKey)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun String.isPKCS1(): Boolean = this.contains("RSA")

    private fun String.stripToOnlyPrivateKey(): String = this
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\n", "")


    private fun String.base64Decode(): ByteArray = Base64.getDecoder().decode(this)
}