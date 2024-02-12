package no.kvros.ros

import no.kvros.ros.models.ROSAndFilename
import no.kvros.ros.models.ROSWrapperObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val rosService: ROSService,
    @Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/ids")
    fun getROSFilenames(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<ROSIdentifiersResultDTO> {
        val result = rosService.fetchROSFilenames(
            owner = repositoryOwner,
            repository = repositoryName,
            accessToken = githubAccessToken,
        )

        return when (result.status) {
            SimpleStatus.Success -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<ROSContentResultDTO> {
        val result = rosService.fetchROSContent(
            owner = repositoryOwner,
            repository = repositoryName,
            rosId = id,
            accessToken = githubAccessToken,
        )

        return when (result.status) {
            ContentStatus.Success -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/{repositoryOwner}/{repositoryName}")
    fun getAllROSesFromGithub(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<ROSAndFilename>? =
        rosService.fetchAllROSesFromGithub(
            owner = repositoryOwner,
            repository = repositoryName,
            path = defaultROSPath,
            accessToken = githubAccessToken,
        )


    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val response = rosService.createROS(
            owner = repositoryOwner,
            repository = repositoryName,
            accessToken = githubAccessToken,
            content = ros,
        )


        return when (response.status) {
            ProcessingStatus.ROSNotValid,
            ProcessingStatus.EncrptionFailed,
            ProcessingStatus.CouldNotCreateBranch,
            ProcessingStatus.ErrorWhenUpdatingROS
            -> ResponseEntity
                .internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response)

            ProcessingStatus.UpdatedROS,
            -> ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response)
        }
    }

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["text/plain"])
    fun editROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val editResult = rosService.updateROS(
            owner = repositoryOwner,
            repository = repositoryName,
            content = ros,
            rosId = id,
            accessToken = githubAccessToken,
        )

        return when (editResult.status) {
            ProcessingStatus.ROSNotValid,
            ProcessingStatus.EncrptionFailed,
            ProcessingStatus.CouldNotCreateBranch,
            ProcessingStatus.ErrorWhenUpdatingROS
            -> ResponseEntity
                .internalServerError()
                .contentType(MediaType.APPLICATION_JSON)
                .body(editResult)

            ProcessingStatus.UpdatedROS,
            -> ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(editResult)
        }
    }


    @GetMapping("/{repositoryOwner}/{repositoryName}/sentToPublication")
    fun fetchAllDraftsSentToPublication(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<ROSIdentifiersResultDTO> {
        val result = rosService.fetchAllROSDraftsSentToPublication(repositoryOwner, repositoryName, githubAccessToken)

        return when (result.status) {
            SimpleStatus.Success -> ResponseEntity.ok().body(result)
            SimpleStatus.Failure -> ResponseEntity.internalServerError().body(result)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    fun sendROSForPublishing(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String
    ): ResponseEntity<ROSPublishedObjectResultDTO> {
        val result = rosService.publishROS(repositoryOwner, repositoryName, id, githubAccessToken)

        return when (result.status) {
            SimpleStatus.Success -> ResponseEntity.ok().body(result)
            SimpleStatus.Failure -> ResponseEntity.internalServerError().body(result)
        }
    }

}