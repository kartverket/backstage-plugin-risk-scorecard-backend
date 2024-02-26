package no.kvros.ros

import no.kvros.github.GithubAppConnector
import no.kvros.infra.connector.models.MicrosoftIdToken
import no.kvros.infra.connector.models.UserContext
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.security.TokenService
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
    private val tokenService: TokenService
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    fun getROSFilenames(
        @RequestHeader("Microsoft-Id-Token") microsoftIdToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<ROSIdentifiersResultDTO> {
        val validatedMicrosoftUser =
            tokenService.validateUser(microsoftIdToken) ?: return ResponseEntity.status(401).body(
                ROSIdentifiersResultDTO(SimpleStatus.Failure, emptyList())
            )
        val githubAccessTokenFromApp = githubAppConnector.getAccessTokenFromApp(repositoryName)

        val userContext =
            UserContext(MicrosoftIdToken(microsoftIdToken), githubAccessTokenFromApp, validatedMicrosoftUser.email)

        val result = rosService.fetchAllROSes(
            owner = repositoryOwner,
            repository = repositoryName,
            userContext = userContext,
        )

        return when (result.status) {
            SimpleStatus.Success -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @RequestHeader("Microsoft-Access-Token") microsoftAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<List<ROSContentResultDTO>> {
        val result = rosService.fetchAllROSes(repositoryOwner, repositoryName, githubAccessToken)

        val successResults = result.filter { it.status == ContentStatus.Success }

        return when {
            successResults.isNotEmpty() -> ResponseEntity.ok().body(successResults)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @RequestHeader("Microsoft-Access-Token") microsoftAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val response =
            rosService.createROS(
                owner = repositoryOwner,
                repository = repositoryName,
                accessToken = githubAccessToken,
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

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["text/plain"])
    fun editROS(
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @RequestHeader("Microsoft-Access-Token") microsoftAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val editResult =
            rosService.updateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                content = ros,
                rosId = id,
                accessToken = githubAccessToken,
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
        @RequestHeader("Github-Access-Token") githubAccessToken: String,
        @RequestHeader("Microsoft-Access-Token") microsoftAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<PublishROSResultDTO> {
        val result = rosService.publishROS(repositoryOwner, repositoryName, id, githubAccessToken)

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }
}
