package no.risc.risc

import no.risc.github.GithubAppConnector
import no.risc.github.GithubStatus
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
    fun getRiScFilenames(
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
    fun fetchRiSc(
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
    fun createNewRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ResponseEntity<ProcessRiScResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessRiScResultDTO.INVALID_USER_CONTEXT)

        val response =
            riScService.createRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                userContext = userContext,
                content = riSc,
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
    fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ResponseEntity<ProcessRiScResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(ProcessRiScResultDTO.INVALID_USER_CONTEXT)

        val editResult =
            riScService.updateRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                content = riSc,
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
    fun sendRiScForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): ResponseEntity<PublishRiScResultDTO> {
        val userContext =
            getUserContext(gcpAccessToken, repositoryName)

        if (!userContext.isValid()) return ResponseEntity.status(401).body(PublishRiScResultDTO.INVALID_USER_CONTEXT)

        val result =
            riScService.publishRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                riScId = id,
                userContext = userContext,
            )

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @GetMapping("/schemas/latest")
    fun fetchLatestJSONSchema(
    ): ResponseEntity<String> {
        val result = riScService.fetchLatestJSONSchema()
        return when (result.status) {
            GithubStatus.Success -> ResponseEntity.ok().body(result.data)
            else -> ResponseEntity.internalServerError().body(result.status.toString())
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
