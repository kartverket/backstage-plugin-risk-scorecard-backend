package no.risc.google

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.exception.exceptions.FetchException
import no.risc.google.model.CryptoKeyPermission
import no.risc.google.model.FetchGcpProjectIdsResponse
import no.risc.google.model.GcpIAMPermission
import no.risc.google.model.GcpProject
import no.risc.google.model.GcpProjectId
import no.risc.google.model.TestIAMPermissionBody
import no.risc.google.model.getRiScCryptoKeyResourceId
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.GoogleOAuthApiConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus

class GoogleServiceIntegrationTests {
    private lateinit var googleService: GoogleServiceIntegration
    private lateinit var webClient: MockableWebClient
    private lateinit var additionalAllowedGCPProjectIds: MutableList<String>

    @BeforeEach
    fun beforeEach() {
        additionalAllowedGCPProjectIds = mutableListOf()
        webClient = MockableWebClient()
        mockGoogleService(additionalAllowedGCPProjectIds)
    }

    fun mockGoogleService(additionalAllowedGCPProjectIds: List<String>) {
        googleService =
            GoogleServiceIntegration(
                googleOAuthApiConnector = mockk<GoogleOAuthApiConnector>().also { every { it.webClient } returns webClient.webClient },
                gcpCloudResourceApiConnector =
                    mockk<GcpCloudResourceApiConnector>().also {
                        every { it.webClient } returns
                            webClient.webClient
                    },
                gcpKmsApiConnector = mockk<GcpKmsApiConnector>().also { every { it.webClient } returns webClient.webClient },
                additionalAllowedGCPProjectIds = additionalAllowedGCPProjectIds,
            )
    }

    /**
     * Helper function for allowing additional GCP key names. This function is needed, as the Google service must be
     * re-mocked each time an additional GCP key name is added to the allowed GCP key names, to propagate the changes to
     * the private property in GoogleServiceIntegration.
     */
    fun allowGCPProjectId(projectId: String) {
        additionalAllowedGCPProjectIds.add(projectId)
        mockGoogleService(additionalAllowedGCPProjectIds)
    }

    @Test
    fun `test validate access token with valid GCP access token`() {
        val accessToken = "validAccessToken"

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
            path = "?access_token=$accessToken",
        )

        val isAccessTokenValid = runBlocking { googleService.validateAccessToken(accessToken) }

        assertTrue(
            isAccessTokenValid,
            "A GCP access token should be considered valid when the information returned by a call to the OAuth2 validation endpoint says it is valid.",
        )
    }

    @Test
    fun `test validate access token with invalid GCP access token`() {
        val accessToken = "invalidAccessToken"

        // The endpoint returns a response with a 400 BAD REQUEST status when the access token is invalid.
        webClient.queueResponse(
            response = MockableResponse(content = "", httpStatus = HttpStatus.BAD_REQUEST),
            path = "?access_token=$accessToken",
        )

        val isAccessTokenValid = runBlocking { googleService.validateAccessToken(accessToken) }

        assertFalse(
            isAccessTokenValid,
            "A GCP access token should not be considered valid when no information is returned on it.",
        )
    }

    private val fetchGCPProjectIdsURL = "/v1/projects"

    private fun iamPermissionURL(projectId: String): String =
        "/v1/${GcpProjectId(projectId).getRiScCryptoKeyResourceId()}:testIamPermissions"

    @Test
    fun `test get GCP crypto keys`() {
        val project1Id = "project1-prod-test"
        val project2Id = "test-project-prod-test"

        val projectIDs =
            FetchGcpProjectIdsResponse(
                projects =
                    listOf(
                        GcpProject(projectId = project1Id),
                        GcpProject(projectId = project2Id),
                    ),
            )

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)

        val permissionsKey1 =
            TestIAMPermissionBody(
                permissions =
                    listOf(
                        GcpIAMPermission.USE_TO_DECRYPT,
                        GcpIAMPermission.USE_TO_ENCRYPT,
                    ),
            )

        val permissionsKey2 =
            TestIAMPermissionBody(permissions = listOf(GcpIAMPermission.USE_TO_DECRYPT))

        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsKey1),
            path = iamPermissionURL(project1Id),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsKey2),
            path = iamPermissionURL(project2Id),
        )

        val cryptoKeys = runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }

        assertEquals(2, cryptoKeys.size, "Both crypto keys should be returned")
        assertTrue(
            cryptoKeys.any { it.projectId == project1Id && it.userPermissions.size == 2 },
            "The crypto keys should include key from project $project1Id with expected number of permissions",
        )
        assertTrue(
            cryptoKeys.any { it.projectId == project2Id && it.userPermissions.size == 1 },
            "The crypto keys should include key from project $project2Id with expected number of permissions",
        )
    }

    @Test
    fun `test get GCP crypto keys can't get project IDs`() {
        webClient.queueResponse(response = MockableResponse(content = "", httpStatus = HttpStatus.BAD_REQUEST))

        assertThrows<FetchException>("getGCPCryptoKeys should throw a FetchException when the call to get project IDs fails.") {
            runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }
        }
    }

    @Test
    fun `test get GCP crypto keys can't get IAM permissions`() {
        val projectId = "project-prod-test"
        val projectIDs = FetchGcpProjectIdsResponse(projects = listOf(GcpProject(projectId = projectId)))

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)
        webClient.queueResponse(
            response = MockableResponse(content = "", httpStatus = HttpStatus.BAD_REQUEST),
            path = iamPermissionURL(projectId),
        )

        assertThrows<FetchException>("getGCPCryptoKeys should throw a FetchException when a call to get IAM permissions fails") {
            runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }
        }
    }

    @Test
    fun `test get GCP crypto keys non-production project ids are ignored`() {
        val project1Id = "project1-prod-test"
        val project2Id = "test-project"

        val projectIDs =
            FetchGcpProjectIdsResponse(
                projects =
                    listOf(
                        GcpProject(projectId = project1Id),
                        GcpProject(projectId = project2Id),
                    ),
            )

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)

        val permissionsKey1 =
            TestIAMPermissionBody(
                permissions =
                    listOf(
                        GcpIAMPermission.USE_TO_DECRYPT,
                        GcpIAMPermission.USE_TO_ENCRYPT,
                    ),
            )

        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsKey1),
            path = iamPermissionURL(project1Id),
        )

        val cryptoKeys = runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }

        assertEquals(1, cryptoKeys.size, "Only the key containing \"-prod-\" should be considered")
        assertTrue(
            cryptoKeys.any { it.projectId == project1Id && it.userPermissions.size == 2 },
            "The crypto keys should include key from project $project1Id with expected number of permissions",
        )
        assertTrue(
            cryptoKeys.all { it.projectId != project2Id },
            "The crypto keys should not include the non-production key ($project2Id).",
        )
    }

    @Test
    fun `test get GCP crypto keys extra keys based on specified project ids are included`() {
        val project1Id = "project1-prod-test"
        val project2Id = "test-project"
        allowGCPProjectId(project2Id)

        val projectIDs =
            FetchGcpProjectIdsResponse(
                projects =
                    listOf(
                        GcpProject(projectId = project1Id),
                        GcpProject(projectId = project2Id),
                    ),
            )

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)

        val permissionsKey1 =
            TestIAMPermissionBody(
                permissions =
                    listOf(
                        GcpIAMPermission.USE_TO_DECRYPT,
                        GcpIAMPermission.USE_TO_ENCRYPT,
                    ),
            )

        val permissionsKey2 =
            TestIAMPermissionBody(permissions = listOf(GcpIAMPermission.USE_TO_DECRYPT))

        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsKey1),
            path = iamPermissionURL(project1Id),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsKey2),
            path = iamPermissionURL(project2Id),
        )

        val cryptoKeys = runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }

        assertEquals(2, cryptoKeys.size, "Both the production key and the extra configured key should be considered")
        assertTrue(
            cryptoKeys.any { it.projectId == project1Id && it.userPermissions.size == 2 },
            "The crypto keys should include the production key ($project1Id) with expected number of permissions.",
        )
        assertTrue(
            cryptoKeys.any { it.projectId == project2Id && it.userPermissions.size == 1 },
            "The crypto keys should include the key ($project2Id) which has been allowed by configuration.",
        )
    }

    @Test
    fun `test get GCP crypto keys filters out non-existent keys`() {
        val existingProjectId = "existing-key-prod-test"
        val nonExistentProjectId = "non-existent-prod-key-test"

        val projectIDs =
            FetchGcpProjectIdsResponse(
                projects =
                    listOf(
                        GcpProject(projectId = existingProjectId),
                        GcpProject(projectId = nonExistentProjectId),
                    ),
            )

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)

        val permissionsExistingKey =
            TestIAMPermissionBody(
                permissions = listOf(GcpIAMPermission.USE_TO_ENCRYPT),
            )

        val permissionsNonExistentKey = TestIAMPermissionBody(permissions = null)

        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsExistingKey),
            path = iamPermissionURL(existingProjectId),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(permissionsNonExistentKey),
            path = iamPermissionURL(nonExistentProjectId),
        )

        val cryptoKeys = runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }

        assertEquals(1, cryptoKeys.size, "Only the existing key should be returned")
        assertTrue(
            cryptoKeys.any { it.projectId == existingProjectId && it.userPermissions.size == 1 },
            "The crypto keys should include the existing key ($existingProjectId) with expected number of permissions.",
        )
        assertTrue(
            cryptoKeys.all { it.projectId != nonExistentProjectId },
            "The crypto keys should not include the non-existent key ($nonExistentProjectId).",
        )
    }

    @Test
    fun `test GCP IAM permission mapping to CryptoKeyPermission`() {
        val encryptOnlyProjectId = "encrypt-only-prod-test"
        val decryptOnlyProjectId = "decrypt-only-prod-test"
        val bothPermissionsProjectId = "both-permissions-prod-test"
        val noPermissionsProjectId = "no-permissions-prod-test"

        val projectIDs =
            FetchGcpProjectIdsResponse(
                projects =
                    listOf(
                        GcpProject(projectId = encryptOnlyProjectId),
                        GcpProject(projectId = decryptOnlyProjectId),
                        GcpProject(projectId = bothPermissionsProjectId),
                        GcpProject(projectId = noPermissionsProjectId),
                    ),
            )

        webClient.queueResponse(response = mockableResponseFromObject(projectIDs), path = fetchGCPProjectIdsURL)

        // Test encrypt only
        val encryptOnlyPermissions =
            TestIAMPermissionBody(
                permissions = listOf(GcpIAMPermission.USE_TO_ENCRYPT),
            )

        // Test decrypt only
        val decryptOnlyPermissions =
            TestIAMPermissionBody(
                permissions = listOf(GcpIAMPermission.USE_TO_DECRYPT),
            )

        // Test both permissions
        val bothPermissions =
            TestIAMPermissionBody(
                permissions =
                    listOf(
                        GcpIAMPermission.USE_TO_ENCRYPT,
                        GcpIAMPermission.USE_TO_DECRYPT,
                    ),
            )

        // Test no permissions (null or empty)
        val noPermissions =
            TestIAMPermissionBody(permissions = null)

        webClient.queueResponse(
            response = mockableResponseFromObject(encryptOnlyPermissions),
            path = iamPermissionURL(encryptOnlyProjectId),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(decryptOnlyPermissions),
            path = iamPermissionURL(decryptOnlyProjectId),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(bothPermissions),
            path = iamPermissionURL(bothPermissionsProjectId),
        )
        webClient.queueResponse(
            response = mockableResponseFromObject(noPermissions),
            path = iamPermissionURL(noPermissionsProjectId),
        )

        val cryptoKeys = runBlocking { googleService.getGcpCryptoKeys(gcpAccessToken = GCPAccessToken("testToken")) }

        // Should have 3 keys (encrypt-only, decrypt-only, both), excluding the one with no permissions
        assertEquals(3, cryptoKeys.size, "Should return 3 keys with permissions")
        assertTrue(
            cryptoKeys.any { it.projectId == encryptOnlyProjectId && it.userPermissions.single() == CryptoKeyPermission.ENCRYPT },
            "The crypto key for project $encryptOnlyProjectId should have ENCRYPT permission",
        )
        assertTrue(
            cryptoKeys.any { it.projectId == decryptOnlyProjectId && it.userPermissions.single() == CryptoKeyPermission.DECRYPT },
            "The crypto key for project $decryptOnlyProjectId should have DECRYPT permission",
        )
        assertTrue(
            cryptoKeys.any { it.projectId == bothPermissionsProjectId && it.userPermissions.size == 2 },
            "The crypto key for project $bothPermissionsProjectId should have two permissions",
        )
        assertEquals(
            setOf(CryptoKeyPermission.ENCRYPT, CryptoKeyPermission.DECRYPT),
            cryptoKeys.find { it.projectId == bothPermissionsProjectId }!!.userPermissions,
            "The key for project $bothPermissionsProjectId should have both ENCRYPT and DECRYPT permissions",
        )
    }
}
