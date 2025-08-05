package no.risc.exception

import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.CreatingRiScException
import no.risc.exception.exceptions.DeletingRiScException
import no.risc.exception.exceptions.FetchException
import no.risc.exception.exceptions.GitHubFetchException
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.JSONSchemaFetchException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.exception.exceptions.RiScNotValidOnFetchException
import no.risc.exception.exceptions.RiScNotValidOnUpdateException
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.exception.exceptions.UpdatingRiScException
import no.risc.risc.models.ContentStatus
import no.risc.risc.models.DecryptionFailureDTO
import no.risc.risc.models.DeleteRiScResultDTO
import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.RiScContentResultDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
internal class GlobalExceptionHandler {
    private val logger: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(JSONSchemaFetchException::class)
    fun handleJSONSchemaFetchException(ex: JSONSchemaFetchException): Any {
        logger.error(ex.message, ex)
        return if (ex.onUpdateOfRiSC) {
            ProcessRiScResultDTO(
                riScId = ex.riScId,
                status = ProcessingStatus.ErrorWhenUpdatingRiSc,
                statusMessage = "Could not fetch JSON schema",
            )
        } else {
            RiScContentResultDTO(
                riScId = ex.riScId,
                status = ContentStatus.SchemaNotFound,
                riScStatus = null,
                riScContent = null,
            )
        }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(RiScNotValidOnUpdateException::class)
    fun handleRiScNotValidOnUpdateException(ex: RiScNotValidOnUpdateException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = ex.riScId,
            status = ProcessingStatus.ErrorWhenUpdatingRiSc,
            statusMessage = ex.validationError,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(RiScNotValidOnFetchException::class)
    fun handleRiScNotValidOnFetchException(ex: RiScNotValidOnFetchException): RiScContentResultDTO {
        logger.error(ex.message, ex)
        return RiScContentResultDTO(
            riScId = ex.riScId,
            status = ContentStatus.SchemaValidationFailed,
            riScStatus = null,
            riScContent = null,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(SopsEncryptionException::class)
    fun handleSopsEncryptionException(ex: SopsEncryptionException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = ex.riScId,
            status = ProcessingStatus.ErrorWhenUpdatingRiSc,
            statusMessage = "Could not encrypt RiSc",
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(SOPSDecryptionException::class)
    fun handleSopsDecryptionException(ex: SOPSDecryptionException): DecryptionFailureDTO {
        logger.error(ex.message, ex)
        return DecryptionFailureDTO(
            status = ContentStatus.DecryptionFailed,
            message = ex.message,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(UpdatingRiScException::class)
    fun handleUpdatingRiScException(ex: UpdatingRiScException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = ex.riScId,
            status = ProcessingStatus.ErrorWhenUpdatingRiSc,
            statusMessage = ex.message,
        )
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    @ExceptionHandler(InvalidAccessTokensException::class)
    fun handleInvalidAccessTokensException(ex: InvalidAccessTokensException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO.INVALID_ACCESS_TOKENS
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(CreatePullRequestException::class)
    fun handleCreatePullRequestException(ex: CreatePullRequestException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = "",
            status = ProcessingStatus.ErrorWhenCreatingPullRequest,
            statusMessage = ex.message,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(CreatingRiScException::class)
    fun handleCreatingRiScException(ex: CreatingRiScException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = ex.riScId,
            status = ProcessingStatus.ErrorWhenCreatingRiSc,
            statusMessage = ex.message,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(DeletingRiScException::class)
    fun handleDeletingRiScException(ex: DeletingRiScException): DeleteRiScResultDTO {
        logger.error(ex.message, ex)
        return DeleteRiScResultDTO(
            riScId = ex.riScId,
            status = ProcessingStatus.ErrorWhenDeletingRiSc,
            statusMessage = ex.message,
        )
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    @ExceptionHandler(PermissionDeniedOnGitHubException::class)
    fun handlePermissionDeniedOnGitHubException(ex: PermissionDeniedOnGitHubException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO.INVALID_ACCESS_TOKENS
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ResponseBody
    @ExceptionHandler(RepositoryAccessException::class)
    fun handleNoWriteAccessToRepositoryException(ex: RepositoryAccessException): Any {
        logger.error(ex.message, ex)
        return ex.response
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(AccessTokenValidationFailedException::class)
    fun handleAccessTokenValidationFailedException(ex: AccessTokenValidationFailedException): Any {
        logger.error(ex.message, ex)
        return ex.response
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(GitHubFetchException::class)
    fun handleFetchRepositoryBranchesException(ex: GitHubFetchException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ex.response
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(FetchException::class)
    fun handleFetchException(ex: FetchException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            riScId = "",
            status = ex.status,
            statusMessage = ex.status.message,
        )
    }
}
