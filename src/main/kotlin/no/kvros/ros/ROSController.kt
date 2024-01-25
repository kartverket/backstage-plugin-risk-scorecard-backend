package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/ids/{githubAccessToken}")
    fun getROSFilenamesFromGithubRepository(
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable githubAccessToken: String,
    ): List<String>? =
        ROSService.fetchROSFilenamesFromGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            accessToken = githubAccessToken,
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}/{githubAccessToken}")
    fun getSingleROSFromGithub(
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable githubAccessToken: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROSFromGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            id = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}/{githubAccessToken}", produces = ["text/plain"])
    fun postROSToGithub(
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable githubAccessToken: String,
        @RequestBody ros: ROSWrapperObject,
        @RequestBody rosFileName: String = "kryptert.ros.yaml" // TODO: fjerne default-input?
    ): ResponseEntity<String?> =
        ROSService.postNewROSToGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            rosFilePath = "$defaultROSPath/$rosFileName",
            accessToken = githubAccessToken,
            content = ros,
        )


}
