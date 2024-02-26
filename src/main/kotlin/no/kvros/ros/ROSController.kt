package no.kvros.ros

import no.kvros.github.GithubAppConnector
import no.kvros.infra.connector.models.MicrosoftIdToken
import no.kvros.infra.connector.models.UserContext
import no.kvros.ros.models.ROSWrapperObject
import no.kvros.security.TokenService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
    ): ResponseEntity<List<ROSContentResultDTO>> {
        val userContext =
            getUserContext(microsoftIdToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401)
            .body(listOf(ROSContentResultDTO.INVALID_USER_CONTEXT))


        val result = rosService.fetchAllROSes(
            owner = repositoryOwner,
            repository = repositoryName,
            userContext = userContext,
        )

        return ResponseEntity.ok().body(result)
    }

    @GetMapping("/{repositoryOwner}/{repositoryName}/{id}")
    fun fetchROS(
        @RequestHeader("Microsoft-Id-Token") microsoftIdToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<List<ROSContentResultDTO>> {
        val userContext =
            getUserContext(microsoftIdToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401)
            .body(listOf(ROSContentResultDTO.INVALID_USER_CONTEXT))


        val result = rosService.fetchAllROSes(repositoryOwner, repositoryName, userContext)

        val successResults = result.filter { it.status == ContentStatus.Success }

        return when {
            successResults.isNotEmpty() -> ResponseEntity.ok().body(successResults)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("Microsoft-Id-Token") microsoftIdToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val userContext =
            getUserContext(microsoftIdToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessROSResultDTO.INVALID_USER_CONTEXT)


        val response =
            rosService.createROS(
                owner = repositoryOwner,
                repository = repositoryName,
                accessToken = userContext.githubAccessToken,
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
        @RequestHeader("Microsoft-Id-Token") microsoftIdToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: ROSWrapperObject,
    ): ResponseEntity<ProcessROSResultDTO> {
        val userContext =
            getUserContext(microsoftIdToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessROSResultDTO.INVALID_USER_CONTEXT)

        val editResult =
            rosService.updateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                content = ros,
                rosId = id,
                accessToken = userContext.githubAccessToken,
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
        @RequestHeader("Microsoft-Id-Token") microsoftIdToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<PublishROSResultDTO> {
        val userContext =
            getUserContext(microsoftIdToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(PublishROSResultDTO.INVALID_USER_CONTEXT)

        val result = rosService.publishROS(
            owner = repositoryOwner,
            repository = repositoryName,
            rosId = id,
            accessToken = userContext.githubAccessToken
        )

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    private fun getUserContext(
        microsoftIdToken: String,
        repositoryName: String
    ): UserContext {
        val validatedMicrosoftUser =
            tokenService.validateUser(microsoftIdToken) ?: return UserContext.INVALID_USER_CONTEXT
        val githubAccessTokenFromApp = githubAppConnector.getAccessTokenFromApp(repositoryName)
        val userContext =
            UserContext(MicrosoftIdToken(microsoftIdToken), githubAccessTokenFromApp, validatedMicrosoftUser.email)
        return userContext
    }
}
