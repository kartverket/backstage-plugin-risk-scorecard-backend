package no.kvros.ros

import io.mockk.every
import io.mockk.mockk
import no.kvros.github.GithubAccessToken
import no.kvros.github.GithubAppConnector
import no.kvros.infra.connector.models.Email
import no.kvros.infra.connector.models.MicrosoftIdToken
import no.kvros.security.MicrosoftUser
import no.kvros.security.TokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ROSControllerTest {

    private val rosService = mockk<ROSService>()
    private val githubAppConnector = mockk<GithubAppConnector>()
    private val tokenService = mockk<TokenService>()
    private val rosController = ROSController(rosService, githubAppConnector, tokenService)

    private val arbitraryValidatedMicrosoftId = MicrosoftIdToken("some token")
    private val arbitraryGithubToken = GithubAccessToken("some token2")
    private val arbitraryValidatedMicrosoftUser = MicrosoftUser(Email("some@email.com"))

    @Test
    fun `when microsoft id token is not valid then status Unauhtorized 401 is returned`() {
        val expected = listOf(ROSContentResultDTO.INVALID_USER_CONTEXT)

        every { tokenService.validateUser(any()) } returns null

        val actual = rosController.getROSFilenames(microsoftIdToken = "", repositoryOwner = "", repositoryName = "")

        assertThat(actual.statusCode.value()).isEqualTo(401)
        assertThat(actual.body).isEqualTo(expected)
    }

    @Test
    fun `when microsoft id token is valid then status Ok 200 is returned`() {
        val expected = listOf(
            ROSContentResultDTO(
                "any-ros-id",
                status = ContentStatus.Success,
                ROSStatus.Draft,
                ""
            )
        )

        every { tokenService.validateUser(any()) } returns arbitraryValidatedMicrosoftUser
        every { githubAppConnector.getAccessTokenFromApp(any()) } returns arbitraryGithubToken
        every { rosService.fetchAllROSes(any(), any(), any()) } returns expected

        val actual = rosController.getROSFilenames(
            microsoftIdToken = arbitraryValidatedMicrosoftId.value,
            repositoryOwner = "",
            repositoryName = ""
        )

        assertThat(actual.statusCode.value()).isEqualTo(200)
        assertThat(actual.body).isEqualTo(expected)
    }

    @Test // TODO - bedre respons enn exception
    fun `when github access token cannot be generated an exception is thrown`() {
        every { tokenService.validateUser(any()) } returns arbitraryValidatedMicrosoftUser
        every { githubAppConnector.getAccessTokenFromApp(any()) } throws Exception()

        assertThrows<Exception> {
            rosController.getROSFilenames(
                microsoftIdToken = "",
                repositoryOwner = "",
                repositoryName = ""
            )
        }

    }


}