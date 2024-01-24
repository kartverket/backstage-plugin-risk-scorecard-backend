package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class ROSController(
    private val ROSService: ROSService,
    @Value("\${github.repository.owner}") private val owner: String,
    @Value("\${github.repository.name}") private val repository: String,
    @Value("\${github.repository.ros-folder-path}") private val path: String,
) {
    @GetMapping("/ros/{githubAccessToken}")
    fun getROSesFromGithub(
        @PathVariable githubAccessToken: String,
    ): String? =
        ROSService.fetchROSesFromGithub(
            owner = owner,
            repository = repository,
            path = path,
            accessToken = githubAccessToken,
        )?.last().toString()


    @GetMapping("/ros/ids/{githubAccessToken}")
    fun getROSFilenamesFromGithub(
        @PathVariable githubAccessToken: String,
    ): List<String>? =
        ROSService.fetchROSFilenamesFromGithub(
            owner = owner,
            repository = repository,
            path = path,
            accessToken = githubAccessToken,
        )

    @GetMapping("/ros/single/{id}/{githubAccessToken}")
    fun getSingleROSFromGithub(
        @PathVariable githubAccessToken: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROSFromGithub(
            owner = owner,
            repository = repository,
            path = path,
            id = id,
            accessToken = githubAccessToken,
            )

    @PostMapping("/ros/{githubAccessToken}", produces = ["text/plain"])
    fun postROSToGithub(
        @PathVariable githubAccessToken: String,
        @RequestBody ros: ROSWrapperObject,
        @RequestBody rosFileName: String = "kryptert.ros.yaml"
    ): ResponseEntity<String?> =
        ROSService.postNewROSToGithub(
            owner = owner,
            repository = repository,
            rosFilePath = "$path/$rosFileName",
            accessToken = githubAccessToken,
            content = ros,
        )

    
}
