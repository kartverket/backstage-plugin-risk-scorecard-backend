package no.kvros.encryption

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class SopsEncryptorForYamlTest {
    private val decryptedROS =
        File("src/test/kotlin/no/kvros/encryption/utils/ukryptert.ros.json").readText(Charsets.UTF_8)

    private val sopsEncryptorStrategy = SopsEncryptorStrategy(
        publicKey = "",
        provider = SopsEncryptionKeyProvider.AGE
    )

    @Test
    fun `when ciphertext is not yaml then null is returned`() {
        val ciphertextThatIsJustAString =
            "ENC[AES256_GCM,data:dIgUegL7885F1wvb5jyBjnCfRWgJNbjwAYiVLmFUOr/h8urBBBPe2M25lkw0yZSp,iv:VNceG9qUFh/BD4gdOIxWh1sddRwhSMzyuz1+MhtLCBY=,tag:6UCtfRCmdsGWKi+OatZ1QQ==,type:str]"

        val actual = SopsEncryptorForYaml.decrypt(ciphertextThatIsJustAString, sopsEncryptorStrategy)

        assertThat(actual).isNull()
    }

    @Test
    fun `when ciphertext is yaml then the json equivalent is returned`() {
        val ciphertextThatIsYaml =
            File("src/test/kotlin/no/kvros/encryption/utils/kryptert.ros_test.yaml").readText(Charsets.UTF_8)

        val actual = SopsEncryptorForYaml.decrypt(ciphertextThatIsYaml, sopsEncryptorStrategy)

        assertThat(actual).isEqualTo(decryptedROS)
    }
}