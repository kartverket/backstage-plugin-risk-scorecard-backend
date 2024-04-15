package no.risc.risc

import no.risc.github.GithubAppConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.UserContext
import no.risc.risc.models.RiScWrapperObject
import no.risc.security.AuthService
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
@RequestMapping("/api/risc")
class RiScController(
    private val riScService: RiScService,
    private val githubAppConnector: GithubAppConnector,
) {

    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    fun getROSFilenames(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<List<RiScContentResultDTO>> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) {
            return ResponseEntity.status(401)
                .body(listOf(RiScContentResultDTO.INVALID_USER_CONTEXT))
        }

        val result =
            riScService.fetchAllRiScs(
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
    ): ResponseEntity<List<RiScContentResultDTO>> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) {
            return ResponseEntity.status(401)
                .body(listOf(RiScContentResultDTO.INVALID_USER_CONTEXT))
        }

        val result = riScService.fetchAllRiScs(repositoryOwner, repositoryName, userContext)

        val successResults = result.filter { it.status == ContentStatus.Success }

        return ResponseEntity.ok().body(successResults)
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}", produces = ["text/plain"])
    fun createNewROS(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody ros: RiScWrapperObject,
    ): ResponseEntity<ProcessRiScResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessRiScResultDTO.INVALID_USER_CONTEXT)

        val response =
            riScService.createROS(
                owner = repositoryOwner,
                repository = repositoryName,
                userContext = userContext,
                content = ros,
            )

        return when (response.status) {
            ProcessingStatus.CreatedRiSc,
            ProcessingStatus.UpdatedRiSc,
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
        @RequestBody ros: RiScWrapperObject,
    ): ResponseEntity<ProcessRiScResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessRiScResultDTO.INVALID_USER_CONTEXT)

        val editResult =
            riScService.updateROS(
                owner = repositoryOwner,
                repository = repositoryName,
                content = ros,
                riScId = id,
                userContext = userContext,
            )

        return when (editResult.status) {
            ProcessingStatus.CreatedRiSc,
            ProcessingStatus.UpdatedRiSc,
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
            riScService.publishROS(
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
