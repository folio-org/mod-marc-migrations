package org.folio.marc.migrations.controllers;

import static java.util.Collections.emptyList;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.TRACE;
import static org.apache.logging.log4j.Level.WARN;
import static org.folio.marc.migrations.domain.entities.types.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.marc.migrations.domain.entities.types.ErrorCode.SERVICE_ERROR;
import static org.folio.marc.migrations.domain.entities.types.ErrorCode.VALIDATION_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.folio.marc.migrations.domain.entities.types.ErrorCode;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.spring.exception.NotFoundException;
import org.folio.tenant.domain.dto.Error;
import org.folio.tenant.domain.dto.Errors;
import org.folio.tenant.domain.dto.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Log4j2
@RestControllerAdvice
public class ApiErrorHandler {

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Errors> handleGlobalExceptions(Exception e) {
    logException(WARN, e);
    return buildResponseEntity(e, INTERNAL_SERVER_ERROR, SERVICE_ERROR);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Errors> handleNotFoundExceptions(NotFoundException e) {
    logException(TRACE, e);
    return buildResponseEntity(e, NOT_FOUND, NOT_FOUND_ERROR);
  }

  @ExceptionHandler(ApiValidationException.class)
  public ResponseEntity<Errors> handleApiValidationException(ApiValidationException e) {
    var parameter = new Parameter(e.getFieldName()).value(e.getFieldValue());
    var errorResponse = new Errors();
    errorResponse.addErrorsItem(buildError(e, VALIDATION_ERROR, List.of(parameter)));
    errorResponse.setTotalRecords(1);
    return buildResponseEntity(errorResponse, UNPROCESSABLE_ENTITY);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Errors> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    logException(DEBUG, e);
    var errorResponse = new Errors();
    Optional.of(e.getBindingResult())
      .map(org.springframework.validation.Errors::getAllErrors)
      .orElse(emptyList())
        .forEach(error ->
          errorResponse.addErrorsItem(new Error(error.getDefaultMessage())
            .message(error.getDefaultMessage())
            .type(MethodArgumentNotValidException.class.getSimpleName())
            .code(VALIDATION_ERROR.getValue())
            .addParametersItem(new Parameter(((FieldError) error).getField())
              .value(String.valueOf(((FieldError) error).getRejectedValue())))));
    errorResponse.totalRecords(errorResponse.getErrors().size());
    return buildResponseEntity(errorResponse, UNPROCESSABLE_ENTITY);
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<Errors> handleValidationException(Exception e) {
    logException(DEBUG, e);
    return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Errors> handlerHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    return Optional.ofNullable(e.getCause())
      .map(Throwable::getCause)
      .filter(IllegalArgumentException.class::isInstance)
      .map(IllegalArgumentException.class::cast)
      .map(this::handleValidationException)
      .orElseGet(() -> {
        logException(DEBUG, e);
        return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
      });
  }

  /**
   * Catches and handles all exceptions of type {@link ConstraintViolationException}.
   *
   * @param exception {@link ConstraintViolationException} to process
   * @return {@link ResponseEntity} with {@link Errors} body
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Errors> handleConstraintViolation(ConstraintViolationException exception) {
    logException(DEBUG, exception);
    var errorResponse = new Errors();
    exception.getConstraintViolations().forEach(constraintViolation ->
        errorResponse.addErrorsItem(new Error()
            .message(String.format("%s %s", constraintViolation.getPropertyPath(), constraintViolation.getMessage()))
            .code(VALIDATION_ERROR.getValue())
            .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  private static void logException(Level logLevel, Exception e) {
    log.log(logLevel, "Handling exception", e);
  }

  private static ResponseEntity<Errors> buildResponseEntity(Exception e, HttpStatus status, ErrorCode code) {
    var errorResponse = new Errors()
        .errors(List.of(new Error()
            .message(e.getMessage())
            .type(e.getClass().getSimpleName())
            .code(code.getValue())))
        .totalRecords(1);
    return buildResponseEntity(errorResponse, status);
  }

  private static ResponseEntity<Errors> buildResponseEntity(Errors errorResponse, HttpStatus status) {
    return ResponseEntity.status(status).body(errorResponse);
  }

  private static Error buildError(Exception e, ErrorCode code, List<Parameter> parameters) {
    return new Error(e.getMessage())
      .code(code.getValue())
      .type(e.getClass().getSimpleName())
      .parameters(parameters);
  }
}
