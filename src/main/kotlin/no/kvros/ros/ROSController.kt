package no.kvros.ros

import no.kvros.github.GithubAppConnector
import no.kvros.infra.connector.models.GCPAccessToken
import no.kvros.infra.connector.models.UserContext
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.security.AuthService
import org.springframework.http.MediaType
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
    private val rosService: ROSService,
    private val githubAppConnector: GithubAppConnector,
) {

    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    fun getROSFilenames(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<List<ROSContentResultDTO>> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) {
            return ResponseEntity.status(401)
                .body(listOf(ROSContentResultDTO.INVALID_USER_CONTEXT))
        }

        val result =
            rosService.fetchAllROSes(
                owner = repositoryOwner,
                repository = repositoryName,
                userContext = userContext,
            )

        return ResponseEntity.ok().body(result)
    }

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<List<ROSContentResultDTO>> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) {
            return ResponseEntity.status(401)
                .body(listOf(ROSContentResultDTO.INVALID_USER_CONTEXT))
        }

        val result = rosService.fetchAllROSes(repositoryOwner, repositoryName, userContext)

        val successResults = result.filter { it.status == ContentStatus.Success }

        return ResponseEntity.ok().body(successResults)
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessROSResultDTO.INVALID_USER_CONTEXT)

        val response =
            rosService.createROS(
                owner = repositoryOwner,
                repository = repositoryName,
                userContext = userContext,
                content = ros,
            )

        return when (response.status) {
            ProcessingStatus.CreatedROS,
            ProcessingStatus.UpdatedROS,
                 ->
                ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)

            else ->
                ResponseEntity
                    .internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)
        }
    }

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    fun editROS(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessROSResultDTO.INVALID_USER_CONTEXT)

        val editResult =
            rosService.updateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                content = ros,
                rosId = id,
                userContext = userContext,
            )

        return when (editResult.status) {
            ProcessingStatus.CreatedROS,
            ProcessingStatus.UpdatedROS,
                 ->
                ResponseEntity
                    .ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(editResult)

            else ->
                ResponseEntity
                    .internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(editResult)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    fun sendROSForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<PublishROSResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(PublishROSResultDTO.INVALID_USER_CONTEXT)

        val result =
            rosService.publishROS(
                owner = repositoryOwner,
                repository = repositoryName,
                rosId = id,
                userContext = userContext,
            )

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else                                -> ResponseEntity.internalServerError().body(result)
        }
    }

    private fun getUserContext(
        gcpAccessToken: String,
        repositoryName: String,
    ): UserContext {
        val validatedMicrosoftUser = AuthService.getMicrosoftUser()
        val githubAccessTokenFromApp = githubAppConnector.getAccessTokenFromApp(repositoryName)
        val userContext =
            UserContext(
                githubAccessTokenFromApp,
                GCPAccessToken(gcpAccessToken),
                validatedMicrosoftUser,
            )
        return userContext
    }
}
