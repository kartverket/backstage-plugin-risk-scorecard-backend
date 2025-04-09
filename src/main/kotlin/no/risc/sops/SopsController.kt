package no.risc.sops

import no.risc.github.GitHubAppService
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.sops.model.CreateSopsConfigResponseBody
import no.risc.sops.model.SopsConfigRequestBody
import no.risc.sops.model.UpdateSopsConfigResponseBody
import no.risc.validation.getValidationResult
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/sops")
class SopsController(
    private val sopsService: SopsService,
    private val gitHubAppService: GitHubAppService,
) {
    @PutMapping("/{repositoryOwner}/{repositoryName}")
    suspend fun createSopsConfig(
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable("repositoryOwner") repositoryOwner: String,
        @PathVariable("repositoryName") repositoryName: String,
        @RequestBody requestBody: SopsConfigRequestBody,
    ): CreateSopsConfigResponseBody {
        val validationResult = requestBody.getValidationResult()
        return if (validationResult.isValid) {
            sopsService.createSopsConfig(
                repositoryOwner = repositoryOwner,
                repositoryName = repositoryName,
                gcpCryptoKey = requestBody.gcpCryptoKey,
                publicAgeKeys = requestBody.publicAgeKeys,
                accessTokens = AccessTokens(GithubAccessToken(gitHubAccessToken), GCPAccessToken(gcpAccessToken)),
            )
        } else {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, validationResult.message)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}")
    suspend fun updateSopsConfig(
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable("repositoryOwner") repositoryOwner: String,
        @PathVariable("repositoryName") repositoryName: String,
        @RequestBody requestBody: SopsConfigRequestBody,
        @RequestParam ref: String,
    ): UpdateSopsConfigResponseBody {
        val validationResult = requestBody.getValidationResult()
        return if (validationResult.isValid) {
            sopsService.updateSopsConfig(
                repositoryOwner = repositoryOwner,
                repositoryName = repositoryName,
                branch = ref,
                gcpCryptoKey = requestBody.gcpCryptoKey,
                publicAgeKeys = requestBody.publicAgeKeys,
                accessTokens = AccessTokens(GithubAccessToken(gitHubAccessToken), GCPAccessToken(gcpAccessToken)),
            )
        } else {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, validationResult.message)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/openPullRequest/{branch}")
    suspend fun openPullRequest(
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable("repositoryOwner") repositoryOwner: String,
        @PathVariable("repositoryName") repositoryName: String,
        @PathVariable("branch") branch: String,
    ) = sopsService.openPullRequest(
        repositoryOwner = repositoryOwner,
        repositoryName = repositoryName,
        sopsId = branch,
        gitHubAccessToken = gitHubAccessToken,
    )
}
