package no.kvros.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class TokenServiceTest {

    private val tokenService = TokenService()
    private val expiredToken =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImtXYmthYTZxczh3c1RuQndpaU5ZT2hIYm5BdyJ9.eyJhdWQiOiI0ZGI5YTVkNC03NGMzLTRjN2UtYmQ3MS0xMDI5Zjk2YTA5OWMiLCJpc3MiOiJodHRwczovL2xvZ2luLm1pY3Jvc29mdG9ubGluZS5jb20vYzZlYTBiMGItZTg2Ny00NmQ0LWE3MGItNGMwNTg4N2U3ZjZmL3YyLjAiLCJpYXQiOjE3MDg0NDY3MjEsIm5iZiI6MTcwODQ0NjcyMSwiZXhwIjoxNzA4NDUwNjIxLCJhaW8iOiJBV1FBbS84V0FBQUFvNXNYaXFyUDNneWZHelJFSk5hZ2dSYWFNZ2xnUjI0RDJkalYwZUF3bjF1aUU3UjFVZnVZZ2dnN2tvTmF4K3p5TlZoZjYxY0hkS1NoRGF4bUcwRFpyKzNETjZDQktoNHkzUFRJczYxUmw4WU56V08vdkFRWVg3SzR2Z2FuLzhlaiIsImVtYWlsIjoibWFyZW4uc29maWUucmluZ3NieUBiZWtrLm5vIiwibmFtZSI6Ik1hcmVuIFNvZmllIFJpbmdzYnkiLCJvaWQiOiJlZThhZmNhOS01ZGRmLTQzMzktYTI3OC1lYzhkMmI1NjllZDIiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJtYXJlbi5zb2ZpZS5yaW5nc2J5QGJla2subm8iLCJyaCI6IjAuQVVjQUN3dnF4bWZvMUVhbkMwd0ZpSDVfYjlTbHVVM0RkSDVNdlhFUUtmbHFDWnhIQU1RLiIsInN1YiI6ImVOZ2d4WEJMRlRmMkpwcnNYNzZSNWZnaXlGMTBjTGs5MXVPWGtMbFdId28iLCJ0aWQiOiJjNmVhMGIwYi1lODY3LTQ2ZDQtYTcwYi00YzA1ODg3ZTdmNmYiLCJ1dGkiOiI5TE9ReDQzWTJVQ2ZwTDYwY3FBMkFBIiwidmVyIjoiMi4wIn0.qAMNYdqnNrmS41vOkOEQnAHK28Lle89q_XRha9tXLG9FiFx8H3g-dWfl2HbpOoXgGLQcQLVENLfn82zeAIh-FGCrY40X4DuTLJO-Rv2D4StX9HDukuzpYBMdxzTQVFFK72_MSali8kaCnuC-8jk8WFue0rNIVG99udQCH50yLWWuoTZ9NHV5y5t08r5vsY1ggVpsIYUXF_a58JVe96uZ_NpIaDQwsAr4NhjJSTGEmh3IrUgt5SczW7X54N0uHLKcsll4qZUcL6300qMZKqJpAuo_B85P8wFrGlxBCKiTAN7jKzrqR4H3-FHmjm_6NbiWW8LIjrIobj1UjiXlCSd3xw"

    @Disabled("You need to provide an id token that is valid")
    @Test
    fun `when id token is valid a microsoft user with the email is returned`() {
        val microsoftIdToken =
            "ditt gyldige id-token"


        val actual = tokenService.validateUser(microsoftIdToken)

        assertThat(actual).isNotNull
        assertThat(actual!!.email.value.isNotBlank()).isTrue()
    }


    @Disabled
    @Test
    fun `when id token is not valid null is returned`() {
        val actual = tokenService.validateUser(expiredToken)

        assertThat(actual).isNull()
    }
}