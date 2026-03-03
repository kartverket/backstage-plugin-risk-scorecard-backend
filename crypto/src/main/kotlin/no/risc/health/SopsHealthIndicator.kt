package no.risc.health

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
class SopsHealthIndicator(
    @Value("\${sops.version}") val sopsVersion: String,
) : HealthIndicator {
    override fun health(): Health? {
        try {
            val builder = ProcessBuilder("sh", "-c", "sops -v --disable-version-check")
            val env = builder.environment()
            val process = builder.start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val actualSopsVersion = reader.readText()
            val exitCode = process.waitFor()

            if (actualSopsVersion.contains(sopsVersion)) {
                return Health
                    .up()
                    .withDetail("version", sopsVersion)
                    .build()
            } else {
                return Health
                    .down()
                    .withDetail("exit code", exitCode)
                    .withDetail("expected version", sopsVersion)
                    .withDetail("actual version", actualSopsVersion)
                    .withDetail("env", env)
                    .build()
            }
        } catch (e: Exception) {
            return Health.down(e).build()
        }
    }
}
