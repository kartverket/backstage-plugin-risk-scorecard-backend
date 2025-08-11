package no.risc.slack

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SlackService(
    private val slackConnector: SlackConnector,
) {
    private val logger = LoggerFactory.getLogger(SlackService::class.java)

    suspend fun sendFeedback(feedbackMessage: String) =
        try {
            slackConnector.sendFeedback(feedbackMessage)
        } catch (e: Exception) {
            logger.error("Failed to send feedback to Slack", e)
            throw RuntimeException(
                "Failed to send feedback to Slack: ${e.message}",
                e,
            )
        }
}
