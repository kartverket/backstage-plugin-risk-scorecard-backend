package no.risc.slack

import no.risc.infra.connector.WebClientConnector
import no.risc.slack.models.SlackMessageDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Connector for sending feedback messages to a Slack webhook.
 *
 * Uses a configured Slack webhook URL to send messages in JSON format using `SlackMessageDTO`.
 * The message is posted as a simple HTTP POST request using WebClient.
 *
 * @param webhookUrl The Slack webhook URL configured in application properties.
 */

@Component
class SlackConnector(
    @Value("\${slack.feedback.webhook.url}")
    private val webhookUrl: String,
) : WebClientConnector(webhookUrl) {
    suspend fun sendFeedback(message: String) {
        webClient
            .post()
            .bodyValue(SlackMessageDTO(message))
            .retrieve()
            .awaitBody<String>()
    }
}
