package no.risc.encryption

import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.util.UriBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


data class EncryptionRequest(
    val text: String,
    val config: String,
    val gcpAccessToken: String,
    val riScId: String
)

@Component
class CryptoServiceIntegration(
    private val cryptoServiceConnector: CryptoServiceConnector,
) : ISopsEncryption {


    fun encryptPost(text: String, _config: String, gcpAccessToken: GCPAccessToken, riScId: String): String {

        val encryptionRequest =
            EncryptionRequest(text = text, config = _config, gcpAccessToken = gcpAccessToken.value, riScId = riScId)

        try {
            val encryptedFile = cryptoServiceConnector.webClient.post()
                .uri("/encryptPost")
                .body(BodyInserters.fromValue(encryptionRequest))
                .retrieve()
                .bodyToMono(String::class.java)
                .block().toString()

            return encryptedFile
        } catch (e: Exception) {
            return "Exception caught: ${e.stackTraceToString()}"
        }
    }


    override fun encrypt(text: String, _config: String, gcpAccessToken: GCPAccessToken, riScId: String): String {
        val urltext = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())

        try {
            val encryptedFile = cryptoServiceConnector.webClient.get()
                .uri { uriBuilder: UriBuilder ->
                    uriBuilder.path("/encrypt")
                        .queryParam("text", urltext)
                        .queryParam("config", _config)
                        .queryParam("gcpAccessToken", gcpAccessToken.value)
                        .queryParam("riScId", riScId)
                        .build()
                }.retrieve().bodyToMono(String::class.java).block().toString()


            return encryptedFile
        } catch (e: Exception) {
            return "Exception caught: ${e.stackTraceToString()}"
        }
    }


    override fun decrypt(ciphertext: String, gcpAccessToken: GCPAccessToken, agePrivateKey: String): String {
        TODO("Not yet implemented")
    }
}