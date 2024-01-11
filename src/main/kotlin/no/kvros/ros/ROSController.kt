package no.kvros.ros

import no.kvros.encryption.SopsEncryptorForYaml
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.validation.JSONValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.security.PublicKey

@RestController
@RequestMapping("/api")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${sops.publicKey}")
    private val publicKey: String
) {
    @GetMapping("/ros/{githubAccessToken}")
    fun getROSesFromGithub(
        @PathVariable githubAccessToken: String,
    ): String? =
        ROSService.fetchROSesFromGithub(
            "bekk",
            "kv-ros-backend",
            ".sikkerhet/ros",
            githubAccessToken,
        ).toString()

    @PostMapping(value = ["/ros/{githubAccessToken}"])
    @ResponseStatus(HttpStatus.CREATED)
    fun postROS(@RequestBody ros: ROSWrapperObject, @PathVariable githubAccessToken: String): String?{
    val msg = ROSService.postROSToGithub(ros)
    return msg
    }
}
