package no.risc.google

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.GoogleOAuthApiConnector
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class GoogleServiceIntegrationTests {
    private lateinit var googleService: GoogleServiceIntegration
    private lateinit var webClient: MockableWebClient
    private lateinit var additionalAllowedGCPKeyNames: MutableList<String>

    @BeforeEach
    fun beforeEach() {
        additionalAllowedGCPKeyNames = mutableListOf()
        webClient = MockableWebClient()
        googleService =
            GoogleServiceIntegration(
                googleOAuthApiConnector = mockk<GoogleOAuthApiConnector>().also { every { it.webClient } returns webClient.webClient },
                gcpCloudResourceApiConnector =
                    mockk<GcpCloudResourceApiConnector>().also {
                        every { it.webClient } returns
                            webClient.webClient
                    },
                gcpKmsApiConnector = mockk<GcpKmsApiConnector>().also { every { it.webClient } returns webClient.webClient },
                additionalAllowedGCPKeyNames = additionalAllowedGCPKeyNames,
            )
    }

    @Test
    fun `test validate access token with valid GCP access token`() {
        webClient.queueResponse(
            response =
                MockableResponse(
                    // Example response provided in the API endpoint documentation for a valid access token.
                    // See: https://cloud.google.com/docs/authentication/token-types#access
                    content =
                        """
                        {
                          "azp": "32553540559.apps.googleusercontent.com",
                          "aud": "32553540559.apps.googleusercontent.com",
                          "sub": "111260650121245072906",
                          "scope": "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/accounts.reauth",
                          "exp": "1650056632",
                          "expires_in": "3488",
                          "email": "user@example.com",
                          "email_verified": "true"
                        }
                        """.trimIndent(),
                ),
            path = "",
        )

        val isAccessTokenValid =
            runBlocking {
                googleService.validateAccessToken("validAccessToken")
            }

        assertTrue(
            isAccessTokenValid,
            "A GCP access token should be considered valid when the information returned by a call to the OAuth2 validation endpoint says it is valid.",
        )
    }

    @Test
    fun `test validate access token with invalid GCP access token`() {
        // The endpoint returns a response with a 400 BAD REQUEST status when the access token is invalid.
        webClient.queueResponse(
            response = MockableResponse(content = "", httpStatus = HttpStatus.BAD_REQUEST),
            path = "",
        )

        val isAccessTokenValid =
            runBlocking {
                googleService.validateAccessToken("invalidAccessToken")
            }

        assertFalse(
            isAccessTokenValid,
            "A GCP access token should not be considered valid when no information is returned on it.",
        )
    }
}
