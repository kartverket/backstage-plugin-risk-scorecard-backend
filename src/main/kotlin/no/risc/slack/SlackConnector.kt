package no.risc.slack

import no.risc.infra.connector.WebClientConnector
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SlackConnector(
    @Value("\${slack.feedback.webhook.url}")
    private val webhookUrl: String,
) : WebClientConnector("") {
    fun sendFeedBack(message: String) {
        webClient
            .post()
            .uri(webhookUrl)
            .bodyValue(SlackMessageDTO(message))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
    }
}

data class SlackMessageDTO(
    val text: String,
)
