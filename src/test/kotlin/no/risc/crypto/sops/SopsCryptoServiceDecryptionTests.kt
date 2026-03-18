package no.risc.crypto.sops

import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.infra.connector.models.GCPAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SopsCryptoServiceDecryptionTests {
    companion object {
        // OBS! Remember to remove before committing
        val validGCPAccessToken = GCPAccessToken("ditt gyldige token")

        @Suppress("ktlint:standard:max-line-length")
        val invalidGCPAccessToken =
            GCPAccessToken(
                "ya29.a0AeXRPp49V58XuoU6xfdO2qWndhdnExfAt97odE9Crs5PWgXwzc9TN2xbaQyxAsY8tDhRva8TPMGcMPlhCw21NC3nXMR-ROa-TW5VQT6z2bJZD1VDDZJmBQpbMg0_ZISAwB7qiW97eGRM4Pt2nqRkMJKFxAffieFYG8bJmrPkyeO8bgaCgYKAUgSARISFQHGX2MiCvLSwEAjMDXyiIWIkWSiHA0181",
            )

        val ageKey1 = "AGE-SECRET-KEY-18TRT94XGD8SC06JSJX5Q9PFFA9XRR0SYKNCVGVLL0EJTS93YJFSQ89A8RP"
        val ageKey2 = "AGE-SECRET-KEY-1FVTKH7URH7YY4MQCPJ3FWYJAJRJAN9U3YQNNHX7HNSNT4QAUEH6QZWSN8Y"
        val invalidAgeKey = ""

        val clearTextPartOfContent = "hei"

        @Suppress("ktlint:standard:max-line-length")
        val sopsFileWithShamir1 =
            "hei: ENC[AES256_GCM,data:r5bHVoU=,iv:M3YhjRcP7EFSUP5uyjsvUcUVttcnBmM4GPDgdxmUi2A=,tag:UZLHS679wF6jSWAfftz+iQ==,type:str]\n" +
                "hva: ENC[AES256_GCM,data:jgxACAo=,iv:fCpggS7M/eFNC1ce0Gmy/7wOgS6sF2igcJwqVqxDy80=,tag:GjWX6R+5GXFewJ50HVTZPQ==,type:str]\n" +
                "sops:\n" +
                "    shamir_threshold: 1\n" +
                "    kms: []\n" +
                "    gcp_kms:\n" +
                "        - resource_id: projects/spire-ros-5lmr/locations/europe-west4/keyRings/rosene-team/cryptoKeys/ros-as-code-2\n" +
                "          created_at: \"2024-08-06T11:07:17Z\"\n" +
                "          enc: CiQAMvdImh2ugjR7IPggRIHlAmnsgnkrmJJK7/VYS8RGA+SPlpYSSQAWhPfdQsTdmcTh2qD4Rgd5vW6lkdjUV3Hp/jN1ERDzPfQL4YHa0pcqE4WV1WLXHOBb6rlSet2xdyJcJ2FbrNPkynCl3o4+fg8=\n" +
                "    azure_kv: []\n" +
                "    hc_vault: []\n" +
                "    age:\n" +
                "        - recipient: age1g9m644t5s95zk6px9mh2kctajqw3guuq2alntgfqu2au6fdz85lq4uupug\n" +
                "          enc: |\n" +
                "            -----BEGIN AGE ENCRYPTED FILE-----\n" +
                "            YWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSBvNWtXNDJQY2t5cVJUdEp0\n" +
                "            MXM5Zk1FTitucWo2VUtORk9ZVzNGNk83TERzCk9RMmx1M2RRNGZsd2JKamlHNmlK\n" +
                "            YjA0UjNIZTZ4Rk1rMExOcVRLM1FIcWsKLS0tIDY2YWN0d28reWdaTGp4NFUyekcy\n" +
                "            NjJodGNxRlNNbXdlYjM2Q0h3VWlSV1UKfA5PfjTJxu7fcCIvJV5Wd3KirAz+3Jak\n" +
                "            68mpwGaD5VisPKhe1TPrJdcZ/QjzmOIqotoKpYmrzsIm6jYQxGUypw==\n" +
                "            -----END AGE ENCRYPTED FILE-----\n" +
                "    lastmodified: \"2024-08-06T11:07:18Z\"\n" +
                "    mac: ENC[AES256_GCM,data:SZc4vSE4N3lkOpyqn3gLqhPl9E5/zfg+RHK8ZvC8PXxTHYgkVoZUy8o8mHL11wAfhVBlsXpomn/1ASESyzQJ7/dxYNKDUvrywDkiCdnF/OLQi0TElzDKRAhfJHZapCR77TQGGaIFAWZYEsiUiqPGh/f88e9jP8eRqFjAzyEJOME=,iv:wc5WCaQu8UKrml/hn2gCNUnRZeo/38Z8WnZ06S9hJ80=,tag:mexZoBUlgiVJRkugeYfjsA==,type:str]\n" +
                "    pgp: []\n" +
                "    unencrypted_suffix: _unencrypted\n" +
                "    version: 3.9.0"

        @Suppress("ktlint:standard:max-line-length")
        val sopsFileWithShamir2 =
            "hei: ENC[AES256_GCM,data:8DD8C0g=,iv:WegIEU7t+H4eFaBynjK9WpvoJSdzvmE81OVu5FxC62M=,tag:EEI+4l3Y85PhpwiBsfnEkw==,type:str]\n" +
                "hva: ENC[AES256_GCM,data:ybxaqB8=,iv:xRxprceL9Fj3QGS0RRTjg63R23xJxfjYsJw1pHuU4PE=,tag:ZPuwrwSK79OSUG/8nAts4A==,type:str]\n" +
                "sops:\n" +
                "    shamir_threshold: 2\n" +
                "    key_groups:\n" +
                "        - hc_vault: []\n" +
                "          age:\n" +
                "            - recipient: age1g9m644t5s95zk6px9mh2kctajqw3guuq2alntgfqu2au6fdz85lq4uupug\n" +
                "              enc: |\n" +
                "                -----BEGIN AGE ENCRYPTED FILE-----\n" +
                "                YWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSB1b1dqSmo1YWJSUHVXejhB\n" +
                "                Wkxwa2ttWlZBV2szYTZDUnh3c2xMWS8rbFhzCm9vRjFHbDJUNjFtTE5WUVZoWVln\n" +
                "                Wm9wd05UZGlOSzNqRUdxYmdZY0NxNHMKLS0tIHBvZTQ5UFNDR1Q5MUhRMkdmS1hW\n" +
                "                TG5Gb0RRamtrWUM0L1VjRytXTlRmcE0KV38Q6hW4/6QfTifXG8dbaM3noPDSQtxo\n" +
                "                t6y58dqsEa9Gm0d+WTbZM2wkEaxxhI8TOM7V9ahwThs6MvYdhkYI3mA=\n" +
                "                -----END AGE ENCRYPTED FILE-----\n" +
                "        - gcp_kms:\n" +
                "            - resource_id: projects/spire-ros-5lmr/locations/eur4/keyRings/ROS/cryptoKeys/ros-as-code\n" +
                "              created_at: \"2024-08-06T10:44:38Z\"\n" +
                "              enc: CiQAMVUE/3YwwDvWmZ4OilZLbRcTWHFef1ICqj5SFfG9AOwioVISSgD2ZGIpiNAI+AiE5Ccc+r9IGR/ANzRH9UllvtLL8UaTh+MygyhCoEeYnTr1ievaGz2bjY/oR4sj5lkV6Qt6KRlVX8nJTMQrWBJo\n" +
                "          hc_vault: []\n" +
                "          age: []\n" +
                "    kms: []\n" +
                "    gcp_kms: []\n" +
                "    azure_kv: []\n" +
                "    hc_vault: []\n" +
                "    age: []\n" +
                "    lastmodified: \"2024-08-06T10:44:38Z\"\n" +
                "    mac: ENC[AES256_GCM,data:6KCDbaVisMHW8T/qrAjqG1ir61XGEF996eSTDpsK16VBSEQck6A+/AY5kq9s/UpCKOFKPWqoC6BemCxb6qRAJjpxuq4f5Nzqv9NfS9RFX9qUJPo83UdlDe/eCzUekD4aYjIuLy88qAqv70MuR2GcN0LLCSe8rI8JZNZQ6PXcWlg=,iv:me0zxeGzJPOEWv/fiEzpr82jracN2JjEOVf+AoL61pI=,tag:Ul8XzoMeAjd2yhAjvxRfSw==,type:str]\n" +
                "    pgp: []\n" +
                "    unencrypted_suffix: _unencrypted\n" +
                "    version: 3.9.0"

        class DecryptionParameters(
            val gcpAccessToken: GCPAccessToken,
            val ageKey: String,
            val cipherText: String,
        )

        @Suppress("ktlint:standard:max-line-length")
        val cipherTextWithAge1 =
            "hei: ENC[AES256_GCM,data:LwQHXY0=,iv:KZ1W3FxTubvbmeQl6fjHfvIh7J7T2bEGD5PaF8INGBM=,tag:3HcsDICtwqGIg3dfMJifuA==,type:str]\n" +
                "hva: ENC[AES256_GCM,data:e6++Q+U=,iv:0noiujc8o+mTrQw0CNWUHcQs7G1yjBdnIYt3aoBhuus=,tag:LjeBxw1ZBSowRqNhRC922Q==,type:str]\n" +
                "sops:\n" +
                "    shamir_threshold: 2\n" +
                "    key_groups:\n" +
                "        - hc_vault: []\n" +
                "          age:\n" +
                "            - recipient: age1g9m644t5s95zk6px9mh2kctajqw3guuq2alntgfqu2au6fdz85lq4uupug\n" +
                "              enc: |\n" +
                "                -----BEGIN AGE ENCRYPTED FILE-----\n" +
                "                YWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSBxZ2RsK1Z6eWRQUmhINlNn\n" +
                "                cTRWTWMyMjdrZStzaGNjQVJrdWJVcWo4QUFFCjlqVkhDTUJKeVFIb0svSy9tS0tn\n" +
                "                ajd4dTBMUXorb3J1WkE1NnpxNkFQamMKLS0tIENuYUNSWGxOTnNLY2MzL2hJUGlt\n" +
                "                amJGUERraElZUCtXYTRCengzYXpOK2MKxiDIzA8jytgsJn0cvkX4i1jKmpxqEmzx\n" +
                "                LZUFQ8GIOUFu3dfueS0JAaxLMywQ9jGTiPwM/MGIycxG8o4bx1MeIts=\n" +
                "                -----END AGE ENCRYPTED FILE-----\n" +
                "        - gcp_kms:\n" +
                "            - resource_id: projects/spire-ros-5lmr/locations/europe-west4/keyRings/rosene-team/cryptoKeys/ros-as-code-2\n" +
                "              created_at: \"2024-08-07T10:02:21Z\"\n" +
                "              enc: CiQAMvdImiH53mjZbagHznhyPOZBDZfoXtvG1x+km3eF9gbxhuwSSgAWhPfdyibcwoAsb/vfqs9sMJ6Z93UDRoArEPHDCuW76pHO6MTb2sfgJGDddv2SE12Rie/J13drgFHJO3PaBRAs4rIh0OCJhbLj\n" +
                "          hc_vault: []\n" +
                "          age: []\n" +
                "    kms: []\n" +
                "    gcp_kms: []\n" +
                "    azure_kv: []\n" +
                "    hc_vault: []\n" +
                "    age: []\n" +
                "    lastmodified: \"2024-08-07T10:02:21Z\"\n" +
                "    mac: ENC[AES256_GCM,data:l0SqUJ3ZWXmaY8gA2rTdoUq/RKBw4O52KRIdWRd0EAXI3Xf1ZyXYHBKa1UF82geZXKPFqKAMtVRyxRpwtuBRAEsd96pGIJI31ko0HRJrhSEg9CZqI1pfW0gbN+1qH++kaFoV8+otJ3eZfCTy1h4jm3EOYNrR5gJlNMGm+4ib4nw=,iv:mhD1UCWSOe9iOEoBMeCs9EqfY2yxp021D9MKuGaP6RE=,tag:kjRBm9eYIZ1n9d39D9edDQ==,type:str]\n" +
                "    pgp: []\n" +
                "    unencrypted_suffix: _unencrypted\n" +
                "    version: 3.9.0"

        @Suppress("ktlint:standard:max-line-length")
        val cipherTextWithAge2 =
            "hei: ENC[AES256_GCM,data:mO8KEj0=,iv:HBAq6EcL9xz4ByW+BtSxoozvHFaKLQ+Rc1+I4/JeWQQ=,tag:ipyXK343QfcuzEcdcEc13g==,type:str]\n" +
                "hva: ENC[AES256_GCM,data:D16Q6/M=,iv:8Do4gzPCHCs/RhvGlan2IQGcs9NQsWu9GsFjUylqtvU=,tag:lddQe0BQwI1DZ0uurNVuUg==,type:str]\n" +
                "sops:\n" +
                "    shamir_threshold: 2\n" +
                "    key_groups:\n" +
                "        - hc_vault: []\n" +
                "          age:\n" +
                "            - recipient: age1u2j2lqd97qwfaynnx324jf9g8lfhvucslwa286yfwva4wjrw75zq5u9qfr\n" +
                "              enc: |\n" +
                "                -----BEGIN AGE ENCRYPTED FILE-----\n" +
                "                YWdlLWVuY3J5cHRpb24ub3JnL3YxCi0+IFgyNTUxOSBPSnluV0NxMmFOQ0dZb3M2\n" +
                "                N2M2QVJjMmc2WTlRRHk0SDJkVy9IZWphN3dRCmtFZ2svak43WWlubzk0K25hODhS\n" +
                "                YkZ2ZVkzMEhIeU0zdktYR3ZTTHRsY00KLS0tIE1kODFVVlE3ZkZJUS8rOU1GSFhI\n" +
                "                VmRVZmhzYWF3Z1VLUXhoYy9HTityQzgKa1Nvogc0SBgmlejyV3g2wLQ4EuCwokvk\n" +
                "                ciGw1YFhvKq2KUVtVGwMmCUYutNddDXSehP1TmplWO6Dq+H0WJc4bxA=\n" +
                "                -----END AGE ENCRYPTED FILE-----\n" +
                "        - gcp_kms:\n" +
                "            - resource_id: projects/spire-ros-5lmr/locations/global/keyRings/sops-test/cryptoKeys/sopskey1\n" +
                "              created_at: \"2024-08-07T10:02:28Z\"\n" +
                "              enc: CiQAE5QSj2zUooNY7a+AHFJ1DC5CggddKEXxS5Ee8Von5Kv1aqsSSgDjvMzLDrYCuj0jtAOnGxYYpLj5gPtmHtKGJPwHkZ+gI7dIbCQ08UF+8BuH4rMXqYdqAvAa9ui2AJp2czxv/cKjFjA3e1hzd0TD\n" +
                "          hc_vault: []\n" +
                "          age: []\n" +
                "    kms: []\n" +
                "    gcp_kms: []\n" +
                "    azure_kv: []\n" +
                "    hc_vault: []\n" +
                "    age: []\n" +
                "    lastmodified: \"2024-08-07T10:02:28Z\"\n" +
                "    mac: ENC[AES256_GCM,data:qk0PScNnb0kY3QBHtJl6MPsFydDBGXAdLGsRTNtZ+DJNwwszCSG2RuhP5hi5yhgoY36jlx8g2zJQ/SnEU4VtiLKD03H50mzXzCRNad0ErxG4UDlxJXZwGJTNXORM0CEHj8MEaadrx6iUa8aowCVzgSkpxaBt4S1zrFcZ964tAvY=,iv:t1YwioO7CH5x2POpCaRLIgdle5PhZaUGnP4Mwl/Xp8g=,tag:FUj8KzcNDvEOBLuwit16zw==,type:str]\n" +
                "    pgp: []\n" +
                "    unencrypted_suffix: _unencrypted\n" +
                "    version: 3.9.0"

        val arbitraryDecryptionParameters1 =
            DecryptionParameters(
                gcpAccessToken = validGCPAccessToken,
                ageKey = ageKey1,
                cipherText = cipherTextWithAge1,
            )

        val arbitraryDecryptionParameters2 =
            DecryptionParameters(
                gcpAccessToken = validGCPAccessToken,
                ageKey = ageKey2,
                cipherText = cipherTextWithAge2,
            )

        @JvmStatic
        fun listOfDecryptionParameters(): Stream<Arguments> =
            Stream.of(
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters1),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters2),
                Arguments.of(arbitraryDecryptionParameters2),
            )

        val sopsCryptoService =
            SopsCryptoService(
                SopsCryptoProperties(
                    backendPublicKey = "age1backend",
                    securityTeamPublicKey = "age1securityteam",
                    securityPlatformPublicKey = "age1securityplatform",
                    agePrivateKey = "AGE-SECRET-KEY-TEST",
                ),
            )
    }

    @Test
    fun `when age key is present and shamir is 1 the ciphertext is successfully decrypted`() {
        sopsCryptoService.decrypt(sopsFileWithShamir1, invalidGCPAccessToken, ageKey1)
    }

    @Disabled("Integration test: requires valid GCP access token (placeholder fails validation)")
    @Test
    fun `when gcp access token is valid and shamir is 1 the ciphertext is successfully decrypted`() {
        sopsCryptoService.decrypt(sopsFileWithShamir1, validGCPAccessToken, invalidAgeKey)
    }

    @Disabled("Integration test: requires valid GCP access token (placeholder fails validation)")
    @Test
    fun `when age key and gcp access token is present and shamir is 2 the ciphertext is successfully decrypted`() {
        sopsCryptoService.decrypt(sopsFileWithShamir2, validGCPAccessToken, ageKey1)
    }

    @Disabled("Integration test: requires valid GCP access token (placeholder fails validation)")
    @Test
    fun `when age key is not present and gcp access token is valid and shamir is 2 the decryption fails`() {
        assertThrows<Exception> { sopsCryptoService.decrypt(sopsFileWithShamir2, validGCPAccessToken, invalidAgeKey) }
    }

    @Test
    fun `when age and key is present but gcp access token is invalid and shamir is 2 the decryption fails`() {
        assertThrows<Exception> { sopsCryptoService.decrypt(sopsFileWithShamir2, invalidGCPAccessToken, ageKey1) }
    }

    @Disabled("Integration test: requires valid GCP access token (placeholder fails validation)")
    @Execution(ExecutionMode.CONCURRENT)
    @ParameterizedTest
    @MethodSource("listOfDecryptionParameters")
    fun `test that decryption works with concurrency`(decryptionParameters: DecryptionParameters) {
        val result =
            sopsCryptoService.decrypt(
                ciphertext = decryptionParameters.cipherText,
                gcpAccessToken = decryptionParameters.gcpAccessToken,
                sopsAgeKey = decryptionParameters.ageKey,
            )

        assertTrue(result.contains(clearTextPartOfContent))
    }

    @Test
    fun `extractSopsConfig should extract sops configuration from ciphertext with shamir threshold 1`() {
        val sopsConfig = sopsCryptoService.extractSopsConfig(sopsFileWithShamir1)

        // Verify the extracted config contains expected fields
        assertEquals(1, sopsConfig.shamir_threshold)
    }

    @Test
    fun `extractSopsConfig should extract sops configuration from ciphertext with shamir threshold 2`() {
        val sopsConfig = sopsCryptoService.extractSopsConfig(sopsFileWithShamir2)

        // Verify the extracted config contains expected fields
        assertEquals(2, sopsConfig.shamir_threshold)
    }

    @Test
    fun `extractSopsConfig should throw SOPSDecryptionException when sops section is missing`() {
        val ciphertextWithoutSops = "schemaVersion: v1\ntitle: test"
        assertThrows<SOPSDecryptionException> {
            sopsCryptoService.extractSopsConfig(ciphertextWithoutSops)
        }
    }
}
