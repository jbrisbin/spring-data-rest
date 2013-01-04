package org.springframework.data.rest.webmvc.support;

import static java.lang.String.*;

import javax.validation.ConstraintViolation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jon Brisbin
 */
public class ConstraintViolationMessage {

  private final ConstraintViolation<?> violation;

  public ConstraintViolationMessage(ConstraintViolation<?> violation) {
    this.violation = violation;
  }

  @JsonProperty("entity")
  public String getEntity() {
    return violation.getRootBean().getClass().getName();
  }

  @JsonProperty("message")
  public String getMessage() {
    return violation.getMessage();
  }

  @JsonProperty("invalidValue")
  public String getInvalidValue() {
    return format("%s", violation.getInvalidValue());
  }

  @JsonProperty("property")
  public String getProperty() {
    return violation.getPropertyPath().toString();
  }

}
