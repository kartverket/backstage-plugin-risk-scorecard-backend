package no.risc.slack

import no.risc.infra.connector.WebClientConnector
import no.risc.slack.models.SlackMessageDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.awaitBody

@Component
class SlackConnector(
    @Value("\${slack.feedback.webhook.url}")
    private val webhookUrl: String,
) : WebClientConnector(webhookUrl) {
    suspend fun sendFeedBack(message: String) {
        webClient
            .post()
            .bodyValue(SlackMessageDTO(message))
            .retrieve()
            .awaitBody<String>()
    }
}
