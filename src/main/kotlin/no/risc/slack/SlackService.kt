package no.risc.slack

import org.springframework.stereotype.Service

@Service
class SlackService(
    private val slackConnector: SlackConnector,
) {
    fun sendFeedBack(message: String) = slackConnector.sendFeedBack(message = message)
}
