package no.risc.exception

import no.risc.exception.exceptions.*
import no.risc.risc.ContentStatus
import no.risc.risc.DecryptionFailure
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
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
    fun handleJSONSchemaFetchException(ex: JSONSchemaFetchException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            "Could not fetch JSON schema",
        )
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    @ExceptionHandler(RiScNotValidException::class)
    fun handleRiScNotValidException(ex: RiScNotValidException): ProcessRiScResultDTO {
        logger.error(ex.message, ex)
        return ProcessRiScResultDTO(
            ex.riScId,
            ProcessingStatus.ErrorWhenUpdatingRiSc,
            ex.validationError,
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
}
