package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${github.repository.owner}") private val owner: String,
    @Value("\${github.repository.name}") private val repository: String,
    @Value("\${github.repository.ros-folder-path}") private val path: String,
) {
    @GetMapping("/ros/ids")
    fun getROSFilenamesFromGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
    ): List<String>? =
        ROSService.fetchROSFilenamesFromGithub(
            owner = owner,
            repository = repository,
            path = path,
            accessToken = githubAccessToken,
        )

    @GetMapping("/ros/{id}")
    fun getSingleROSFromGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROSFromGithub(
            owner = owner,
            repository = repository,
            path = path,
            id = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/ros", produces = ["text/plain"])
    fun postROSToGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @RequestBody ros: ROSWrapperObject,
        @RequestBody rosFileName: String = "kryptert.ros.yaml",
    ): ResponseEntity<String?> =
        ROSService.postNewROSToGithub(
            owner = owner,
            repository = repository,
            rosFilePath = "$path/$rosFileName",
            accessToken = githubAccessToken,
            content = ros,
        )
}
