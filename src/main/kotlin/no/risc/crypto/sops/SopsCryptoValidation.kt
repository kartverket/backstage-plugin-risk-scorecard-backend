package no.risc.crypto.sops

import no.risc.crypto.sops.utils.toBech32Data
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

private val logger = LoggerFactory.getLogger(SopsCryptoService::class.java)

object SopsCryptoValidation {
    // Regex pattern to match b64token
    private const val B64_TOKEN_PATTERN = "^[-a-zA-Z0-9._~+/]+=*$"

    fun isValidGCPToken(token: String): Boolean {
        if (!Pattern.matches(B64_TOKEN_PATTERN, token)) {
            logger.debug("Invalid GCP Token. Expected b64token")
            return false
        }

        return true
    }

    fun isValidAgeSecretKey(sopsAgeKey: String): Boolean {
        val hrp = "age-secret-key-"

        try {
            val bech32Data = sopsAgeKey.toBech32Data()
            return hrp == bech32Data.hrp
        } catch (e: IllegalArgumentException) {
            logger.debug("Invalid age private key. Expected bech32-encoded value")
            return false
        }
    }
}
