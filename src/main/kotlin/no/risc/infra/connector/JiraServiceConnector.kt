package no.risc.infra.connector

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JiraServiceConnector(
    @Value("\${jira.base-url:https://api.atlassian.com}")
    baseUrl: String,
) : WebClientConnector(baseURL = baseUrl)
