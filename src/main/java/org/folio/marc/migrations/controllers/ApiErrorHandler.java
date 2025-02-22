package org.folio.marc.migrations.controllers;

import static java.util.Collections.emptyList;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.TRACE;
import static org.apache.logging.log4j.Level.WARN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.folio.marc.migrations.exceptions.ApiValidationException;
import org.folio.spring.exception.NotFoundException;
import org.folio.tenant.domain.dto.Error;
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
  public ResponseEntity<Error> handleGlobalExceptions(Exception e) {
    logException(WARN, e);
    return buildUnprocessableResponseEntity(e, INTERNAL_SERVER_ERROR);
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Error> handleNotFoundExceptions(NotFoundException e) {
    logException(TRACE, e);
    return buildUnprocessableResponseEntity(e, NOT_FOUND);
  }

  @ExceptionHandler(ApiValidationException.class)
  public ResponseEntity<Error> handleApiValidationException(ApiValidationException e) {
    var parameter = new Parameter(e.getFieldName()).value(e.getFieldValue());
    return buildUnprocessableResponseEntity(buildError(e, List.of(parameter)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Error> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    logException(DEBUG, e);
    var errors = Optional.of(e.getBindingResult())
      .map(org.springframework.validation.Errors::getAllErrors)
      .orElse(emptyList())
      .stream()
      .map(error -> new Error(error.getDefaultMessage())
        .type(MethodArgumentNotValidException.class.getSimpleName())
        .addParametersItem(new Parameter(((FieldError) error).getField())
          .value(String.valueOf(((FieldError) error).getRejectedValue()))))
      .toList();

    return buildUnprocessableResponseEntity(errors.getFirst());
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<Error> handleValidationException(Exception e) {
    logException(DEBUG, e);
    return buildUnprocessableResponseEntity(e, BAD_REQUEST);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Error> handlerHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    return Optional.ofNullable(e.getCause())
      .map(Throwable::getCause)
      .filter(IllegalArgumentException.class::isInstance)
      .map(IllegalArgumentException.class::cast)
      .map(this::handleValidationException)
      .orElseGet(() -> {
        logException(DEBUG, e);
        return buildUnprocessableResponseEntity(e, BAD_REQUEST);
      });
  }

  private static ResponseEntity<Error> buildUnprocessableResponseEntity(Exception exception, HttpStatus status) {
    return ResponseEntity.status(status).body(buildError(exception, emptyList()));
  }

  private static ResponseEntity<Error> buildUnprocessableResponseEntity(Error errorResponse) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
  }

  private static void logException(Level logLevel, Exception e) {
    log.log(logLevel, "Handling exception", e);
  }

  private static Error buildError(Exception e, List<Parameter> parameters) {
    return new Error(e.getMessage())
      .type(e.getClass().getSimpleName())
      .parameters(parameters);
  }

}
