package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/ids")
    fun getROSFilenames(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<String>? =
        ROSService.fetchROSFilenames(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            accessToken = githubAccessToken,
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROS(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            id = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<String?> =
        ROSService.createNewROS(
            owner = repositoryOwner,
            repository = repositoryName,
            rosFilePath = "$defaultROSPath/${RandomStringUtils.randomAlphanumeric(5)}.ros.yaml",
            accessToken = githubAccessToken,
            content = ros,
        )

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["text/plain"])
    fun editROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<String?> =
        ROSService.createNewROS(
            owner = repositoryOwner,
            repository = repositoryName,
            rosFilePath = "$defaultROSPath/$id.ros.yaml",
            accessToken = githubAccessToken,
            content = ros,
        )
}