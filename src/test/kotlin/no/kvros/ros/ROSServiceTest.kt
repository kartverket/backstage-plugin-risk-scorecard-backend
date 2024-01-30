package no.kvros.ros

import io.mockk.*
import no.kvros.encryption.SopsEncryptorForYaml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ROSServiceTest {
    private val githubConnector: GithubConnector = mockk()
    private val arbitraryKey = ""
    private val rosService = ROSService(githubConnector, arbitraryKey, arbitraryKey)
    private val encryptedROS =
        File("src/test/kotlin/no/kvros/encryption/utils/kryptert.ros_test.yaml").readText(Charsets.UTF_8)
    val arbitraryOwner = "testowner"
    val arbitraryRepository = "repository"
    val arbitraryPathToROS = "pathToRos"
    val arbitraryAccessToken = "accessToken"


    @Test
    fun `service throws exception when the github connector throws exception when fetching ROSes`() {
        every { githubConnector.fetchPublishedROSes(any(), any(), any(), any()) } throws Exception()

        assertThrows<Exception> {
            rosService.fetchROSFilenames(
                arbitraryOwner,
                arbitraryRepository,
                arbitraryPathToROS,
                arbitraryAccessToken
            )
        }
    }

    @Test
    fun `when github connector returns a list with one ROS it is decrypted correctly`() {
        mockkObject(SopsEncryptorForYaml)
        every { githubConnector.fetchPublishedROSes(any(), any(), any(), any()) } returns listOf(encryptedROS)
        every { SopsEncryptorForYaml.decrypt(any(), any()) } returns "some valid json structures"

        val actual = rosService.fetchROSFilenames(
            arbitraryOwner,
            arbitraryRepository,
            arbitraryPathToROS,
            arbitraryAccessToken
        )

        assertThat(actual).isNotNull
        assertThat(actual!!.size).isEqualTo(1)

        verify(exactly = 1) { SopsEncryptorForYaml.decrypt(any(), any()) }
        unmockkObject(SopsEncryptorForYaml)
    }

}