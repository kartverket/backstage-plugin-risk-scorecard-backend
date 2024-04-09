package no.kvros.security

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SigningKeyResolverAdapter
import java.net.URL
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.util.Base64
import no.kvros.infra.connector.models.Email
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TokenService {

    val logger: Logger = LoggerFactory.getLogger(TokenService::class.java)
    fun validateUser(microsoftToken: String): MicrosoftUser? =
        try {
            Jwts.parserBuilder()
                .setSigningKeyResolver(SigningKeyResolver())
                .build()
                .parseClaimsJws(microsoftToken)
                .let {
                    MicrosoftUser(
                        email = Email(it.body["email"] as String),
                        name = it.body["name"] as String
                    )
                }.also {
                    logger.info("Validated user with email: ${it.email}.")
                }
        } catch (e: Exception) {
            logger.error("Failed to validate token with error message: ${e.message}")
            e.printStackTrace()
            null
        }

    inner class SigningKeyResolver : SigningKeyResolverAdapter() {

        override fun resolveSigningKey(jwsHeader: JwsHeader<out JwsHeader<*>>?, claims: Claims?): PublicKey {
            val tid = claims?.get("tid") ?: throw Exception("Tenant ID (tid) missing in JWT")
            val kid = jwsHeader?.keyId ?: throw Exception("Key ID (kid) missing in JWT")
            val jwksJson = URL("https://login.microsoftonline.com/${tid}/discovery/v2.0/keys")
            val jwksMap = ObjectMapper().readValue(jwksJson, Map::class.java)
            val keys = jwksMap["keys"] as List<Map<String, Any>>
            val key = keys.firstOrNull { it["kid"] == kid } ?: throw Exception("Public key not found in JWKS")
            val x5c = (key["x5c"] as List<String>).first()
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(Base64.getDecoder().decode(x5c).inputStream())
                .publicKey
        }
    }
}

data class MicrosoftUser(
    val email: Email, val name: String
)