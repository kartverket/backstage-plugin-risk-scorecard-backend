package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/api")
class ROSController(
    private val ROSService: ROSService,
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
        )?.first().toString()

    @PostMapping("/ros/{githubAccessToken}", produces = ["text/plain"])
    fun postROSToGithub(
        @PathVariable githubAccessToken: String,
        @RequestBody ros: ROSWrapperObject,
    ): String? =
        ROSService.postNewROSToGithub(
            owner = "bekk",
            repository = "kv-ros-backend",
            accessToken = githubAccessToken,
            content = ros
        )
}
