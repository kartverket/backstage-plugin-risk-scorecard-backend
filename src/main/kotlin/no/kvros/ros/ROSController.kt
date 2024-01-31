package no.kvros.ros

import no.kvros.github.GithubPullRequestObject
import no.kvros.ros.models.ROSWrapperObject
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
    ): List<ROSIdentifier>? =
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
            rosId = id,
            accessToken = githubAccessToken,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ROSResult> {
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
    ): ResponseEntity<ROSResult> {
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
    ): ResponseEntity<List<GithubPullRequestObject>> = ResponseEntity.ok()
        .body(rosService.fetchAllROSDraftsSentToPublication(repositoryOwner, repositoryName, githubAccessToken))

    @PostMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    fun sendROSForPublishing(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String
    ): ResponseEntity<GithubPullRequestObject> =
        ResponseEntity.ok().body(rosService.publishROS(repositoryOwner, repositoryName, id, githubAccessToken))

}