package no.kvros.encryption

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class SOPSTest {
    private val decryptedROS =
        File("src/test/kotlin/no/kvros/encryption/utils/ukryptert.ros.json").readText(Charsets.UTF_8)
    private val testAgePublicKey =
        "age18r28lrah6ky42urwww065k6rg50dn35a4wk7llpklnrztmjjx93qaaevuu" // SOPS_AGE_KEY settes for test-miljøvariabler i build.gradle.kts

    @Test
    fun `when ciphertext is not yaml an exception is thrown`() {
        val ciphertextThatIsJustAString =
            "ENC[AES256_GCM,data:dYo75pR4EvbtULEJ926/tm9qZns2n8LHkNg78GpYk41gZGd6awrZ3NVtWVFeu4ns,iv:pjcpGaqDfU0vy76PgF6ZdMOriXNfeANOoYyda8Mq9EA=,tag:Rcv+ZgI1n2fgKy8DSep4jQ==,type:str]"

        assertThrows<Exception> {
            SOPS.decrypt(
                ciphertext = ciphertextThatIsJustAString,
                sopsEncryptorHelper =
                    SopsEncryptorHelper(
                        listOf(
                            SopsProviderAndCredentials(
                                provider = SopsEncryptionKeyProvider.AGE,
                                publicKeyOrPath = testAgePublicKey,
                            ),
                        ),
                    ),
            )
        }
    }

    @Test
    fun `when ciphertext is yaml then the json equivalent is returned`() {
        val ciphertextThatIsYaml =
            File("src/test/kotlin/no/kvros/encryption/utils/kryptert.ros_test.yaml").readText(Charsets.UTF_8)

        val actual =
            SOPS.decrypt(
                ciphertextThatIsYaml,
                SopsEncryptorHelper(
                    listOf(
                        SopsProviderAndCredentials(
                            provider = SopsEncryptionKeyProvider.AGE,
                            publicKeyOrPath = testAgePublicKey,
                        ),
                    ),
                ),
            )

        assertThat(actual).isEqualTo(decryptedROS)
    }
}
