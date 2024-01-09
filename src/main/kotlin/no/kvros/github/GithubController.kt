package no.kvros.github

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class GithubController(
    private val githubService: GithubService,
) {
    @GetMapping("/github/{token}")
    fun fetchROS(
        @PathVariable token: String,
    ): List<String>? =
        githubService.fetchROSes(
            "bekk",
            "kv-ros-backend",
            ".sikkerhet/ros",
            token,
        )
}
