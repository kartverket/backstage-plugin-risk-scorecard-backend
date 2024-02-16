package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import no.kvros.infra.connector.WebClientConnector
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.io.BufferedReader
import java.io.File
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

class GithubAppAccessToken(
    val value: String?
)

class GithubAppIdentifier(
    val appId: Int,
    val installationId: Int
)

class GithubAppConnector(val appIdentifier: GithubAppIdentifier) : WebClientConnector("https://api.github.com/app") {
    private val filePath =
        "/Users/maren/Documents/ros/kv-ros-backend/src/main/kotlin/no/kvros/github/backstage-testis.2024-02-16.private-key.pem" // TODO: lagre denne i gcp eller noe

    fun getGithubAppSignedJWT(backstageToken: String): GithubAppSignedJwt {
        if (backstageToken.isBlank()) return GithubAppSignedJwt(null) // TODO: gjør en ordentlig sjekk her

        return GithubAppSignedJwt(
            PemUtils.getSignedJWT(
                privateKey = File(filePath).readBytes(),
                appId = appIdentifier.appId
            )
        )
    }

    fun getGithubAppAccessToken(jwt: GithubAppSignedJwt): GithubAppAccessToken {
        val accessTokenBody: GithubAccessTokenBody? =
            webClient
                .post()
                .uri(GithubHelper.uriToGetAccessTokenFromInstallation(appIdentifier.installationId.toString()))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${jwt.value}")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(
                    Mono.just(GithubHelper.bodyToCreateAccessTokenForRepository("kv-ros-test-2").toContentBody()),
                    String::class.java
                )
                .retrieve()
                .bodyToMono<GithubAccessTokenBody>()
                .block()

        return GithubAppAccessToken(accessTokenBody?.token)

    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GithubAccessTokenBody(
        val token: String,
    )


    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): WebClient.ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()
}

object PemUtils {
    class PEM_CONVERSION_EXCEPTION() : Exception()

    fun getSignedJWT(privateKey: ByteArray, appId: Int): String {
        val pemContent = String(privateKey, StandardCharsets.UTF_8)
        val privateKey = PemUtils.readPrivateKey(pemContent)

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

    fun readPrivateKey(pem: String): PrivateKey {
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