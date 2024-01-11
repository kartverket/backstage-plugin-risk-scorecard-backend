package no.kvros.ros

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
            owner = "bekk",
            repository = "kv-ros-backend",
            pathToRoser = ".sikkerhet/ros",
            accessToken = githubAccessToken,
        ).toString()

    @PostMapping("/ros/{githubAccessToken}", consumes = ["text/plain"], produces = ["text/plain"])
    fun postROSToGithub(
        @PathVariable githubAccessToken: String,
        @RequestBody ros: String
    ): String =
        ROSService.postNewROSToGithub(
            owner = "bekk",
            repository = "kv-ros-backend",
            accessToken = githubAccessToken,
            content = ros
        ) ?: "søren"
}
