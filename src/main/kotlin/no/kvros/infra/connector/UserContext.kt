package no.kvros.infra.connector

import no.kvros.github.GithubAppAccessToken

data class UserContext(
    val microsoftIdentifier: MicrosoftIdentifier,
    val user: User,
    val githubAccessToken: GithubAppAccessToken? = null
) {
    fun isValid(): Boolean = true // TODO sjekk med public keys
}

data class MicrosoftIdentifier(val accessToken: String, val idToken: String)

data class User(
    val email: Email,
    val relationEntities: List<RelationEntity>,
)


data class RelationEntity(
    val namespace: String,
    val name: String
)

data class SourceEntity(
    val name: String,
    val owner: String,
    val system: String,
    val type: String, // egen enum etter hvert
    val githubUrl: Url
)

data class Email(
    val value: String,
)

data class Url(
    val value: String
)
