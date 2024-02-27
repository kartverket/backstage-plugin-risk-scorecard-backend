package no.kvros.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.jsonwebtoken.JwtException
import no.kvros.infra.connector.models.Email
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URL
import java.security.cert.CertificateFactory
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.*

@Service
class TokenService {
    val logger = LoggerFactory.getLogger(this::class.java)
    fun validateUser(microsoftToken: String): MicrosoftUser? {
        val microsoftUser = try {
            val signedJwt = SignedJWT.parse(microsoftToken)
            val parsedToken = JWTParser.parse(microsoftToken)
            val keyId = (parsedToken.header as JWSHeader).keyID
            val tId = parsedToken.jwtClaimsSet.getClaim("tid") as String
            val key = azureAuthPublicKey(keyId, tId)

            val verifyerFactory = DefaultJWSVerifierFactory()
            val jwsVerifier = verifyerFactory.createJWSVerifier(signedJwt.header, key)

            if (signedJwt.verify(jwsVerifier) && tokenIsValid(parsedToken))
                MicrosoftUser(
                    email = Email(signedJwt.jwtClaimsSet.getStringClaim("email")),
                    name = signedJwt.jwtClaimsSet.getStringClaim("name")
                )
            else null
        } catch (e: JwtException) {
            logger.error("Failed to validate token with error message: ${e.message}")
            null
        }

        return when {
            microsoftUser != null -> {
                logger.info("Validated user with email: ${microsoftUser.email}.")
                microsoftUser
            }

            else -> null
        }
    }

    private fun tokenIsValid(jwt: JWT): Boolean {
        val expirationDate = jwt.jwtClaimsSet.expirationTime


        return expirationDate.after(Date.from(Instant.now()))
    }

    private fun azureAuthPublicKey(kid: String, tenantId: String): RSAPublicKey {
        val jwksJson = URL("https://login.microsoftonline.com/${tenantId}/discovery/v2.0/keys")
        val jwksMap = ObjectMapper().readValue(jwksJson, Map::class.java)
        val keys = jwksMap["keys"] as List<Map<String, Any>>

        val keyIndex = keys.filter { it["kid"] == kid }.firstOrNull()
        if (keyIndex == null) throw Exception("Klarte ikke finne riktig public key")

        val x5c = (keyIndex["x5c"] as List<String>).first()

        return buildRSAPublicKey(x5c)
    }

    private fun buildRSAPublicKey(x5c: String): RSAPublicKey {
        val certificateBytes = Base64.getDecoder().decode(x5c)
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory.generateCertificate(certificateBytes.inputStream())
        val publicKey = certificate.publicKey

        return publicKey as RSAPublicKey
    }
}

data class MicrosoftUser(
    val email: Email,
    val name: String
)