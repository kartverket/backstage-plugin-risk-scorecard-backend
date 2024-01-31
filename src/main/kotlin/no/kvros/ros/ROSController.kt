package no.kvros.ros

import no.kvros.github.GithubPullRequestObject
import no.kvros.ros.models.ROSWrapperObject
import org.apache.commons.lang3.RandomStringUtils
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/ros")
class ROSController(
    private val rosService: ROSService,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/ids")
    fun getROSFilenames(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<String>? =
        rosService.fetchROSFilenames(
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
        rosService.fetchROSContent(
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
            rosService.updateOrCreateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                rosReference = RandomStringUtils.randomAlphanumeric(5),
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
        val editResult = rosService.updateOrCreateROS(
            owner = repositoryOwner,
            repository = repositoryName,
            rosReference = id,
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
    ): ResponseEntity<List<GithubPullRequestObject>> {
        return ResponseEntity.ok()
            .body(rosService.fetchAllROSDraftsSentToPublication(repositoryOwner, repositoryName, githubAccessToken))
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    fun sendROSForPublishing(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String
    ): ResponseEntity<GithubPullRequestObject> {
        return ResponseEntity.ok().body(rosService.publishROS(repositoryOwner, repositoryName, id, githubAccessToken))
    }

}