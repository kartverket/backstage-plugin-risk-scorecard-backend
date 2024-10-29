package no.risc.exception

import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.CreatingRiScException
import no.risc.exception.exceptions.InitializeRiScSessionNotFoundException
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.JSONSchemaFetchException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.exception.exceptions.RiScNotValidOnFetchException
import no.risc.exception.exceptions.RiScNotValidOnUpdateException
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.ScheduleInitialRiScDuringLocalException
import no.risc.exception.exceptions.ScheduleInitialRiScException
import no.risc.exception.exceptions.SopsConfigFetchException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.exception.exceptions.UnableToParseResponseBodyException
import no.risc.exception.exceptions.UpdatingRiScException
import no.risc.risc.ContentStatus
import no.risc.risc.DecryptionFailure
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScContentResultDTO
import no.risc.risc.models.ScheduleInitialRiScDTO
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
                ex.riScId,
                ProcessingStatus.ErrorWhenUpdatingRiSc,
                "Could not fetch JSON schema",
            )
        } else {
            RiScContentResultDTO(
                ex.riScId,
                ContentStatus.SchemaNotFound,
                null,
                null,
            )
        }
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(RiScNotValidOnUpdateException::class)
    fun handleRiScNotValidOnUpdateException(ex: RiScNotValidOnUpdateException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            ex.validationError,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(RiScNotValidOnFetchException::class)
    fun handleRiScNotValidOnFetchException(ex: RiScNotValidOnFetchException): RiScContentResultDTO {
        logger.error(ex.message, ex)
        return RiScContentResultDTO(
            ex.riScId,
            ContentStatus.SchemaValidationFailed,
            null,
            null,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(SopsConfigFetchException::class)
    fun handleSopsConfigFetchException(ex: SopsConfigFetchException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            ex.responseMessage,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(SopsEncryptionException::class)
    fun handleSopsEncryptionException(ex: SopsEncryptionException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            "Could not encrypt RiSc",
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(SOPSDecryptionException::class)
    fun handleSopsDecryptionException(ex: SOPSDecryptionException): DecryptionFailure {
        logger.error(ex.message, ex)
        return DecryptionFailure(
            ContentStatus.DecryptionFailed,
            ex.message,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(UpdatingRiScException::class)
    fun handleUpdatingRiScException(ex: UpdatingRiScException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            ex.message,
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
            ex.riScId,
            ProcessingStatus.ErrorWhenCreatingPullRequest,
            ex.message,
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(CreatingRiScException::class)
    fun handleCreatingRiScException(ex: CreatingRiScException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenCreatingRiSc,
            ex.message,
        )
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(InitializeRiScSessionNotFoundException::class)
    fun handleInitializeRiScSessionNotFoundException(ex: InitializeRiScSessionNotFoundException) {
        logger.error(ex.message, ex)
    }

    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    @ExceptionHandler(ScheduleInitialRiScDuringLocalException::class)
    fun handleScheduleInitialRiScDuringLocalException(ex: ScheduleInitialRiScDuringLocalException): ScheduleInitialRiScDTO {
        logger.error(ex.message, ex)
        return ex.response
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(UnableToParseResponseBodyException::class)
    fun handleUnableToParseResponseBodyException(ex: UnableToParseResponseBodyException) {
        logger.error(ex.message, ex)
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
    @ExceptionHandler(ScheduleInitialRiScException::class)
    fun handleScheduleInitialRiScException(ex: ScheduleInitialRiScException): ScheduleInitialRiScDTO {
        logger.error(ex.message, ex)
        return ex.response
    }

    @ResponseBody
    @ExceptionHandler(AccessTokenValidationFailedException::class)
    fun handleAccessTokenValidationFailedException(ex: AccessTokenValidationFailedException): Any {
        logger.error(ex.message, ex)
        return ex.response
    }
}
