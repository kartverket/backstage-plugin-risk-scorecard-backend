package no.kvros.ros

import no.kvros.ros.models.ROSWrapperObject
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val ROSService: ROSService,
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
            accessToken = githubAccessToken,
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): String? =
        ROSService.fetchROSContent(
            owner = repositoryOwner,
            repository = repositoryName,
            id = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ROSResult> = ResponseEntity
        .ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            ROSService.updateOrCreateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                rosId = RandomStringUtils.randomAlphanumeric(5),
                accessToken = githubAccessToken,
                content = ros,
            )
        )

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["text/plain"])
    fun editROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ROSResult> {
        val editResult = ROSService.updateOrCreateROS(
            owner = repositoryOwner,
            repository = repositoryName,
            rosId = id,
            content = ros,
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

            ProcessingStatus.UpdatedNewBranchCreatedForNewROS,
            ProcessingStatus.UpdatedNewBranchCreatedForExistingROS,
            ProcessingStatus.UpdatedROSOnExistingBranch
            -> ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(editResult)
        }
    }
}