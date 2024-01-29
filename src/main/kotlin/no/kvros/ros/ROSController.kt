package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/ids")
    fun getROSFilenamesFromGithubRepository(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<String>? =
        ROSService.fetchROSFilenamesFromGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            accessToken = githubAccessToken,
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun getSingleROSFromGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROSFromGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            id = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun postROSToGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<String?> =
        ROSService.postNewROSToGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            rosFilePath = "$defaultROSPath/${RandomStringUtils.randomAlphanumeric(5)}.ros.yaml",
            accessToken = githubAccessToken,
            content = ros,
        )

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["text/plain"])
    fun putROSToGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<String?> =
        ROSService.postNewROSToGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            rosFilePath = "$defaultROSPath/$id.ros.yaml",
            accessToken = githubAccessToken,
            content = ros,
        )


    private fun createBranchForNewRos(): StatusDTO {
        return StatusDTO("yes, da ble det ny branch da")
    }
}


data class StatusDTO(
    val message: String,
)