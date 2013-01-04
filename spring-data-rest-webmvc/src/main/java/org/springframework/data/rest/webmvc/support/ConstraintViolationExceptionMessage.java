package org.springframework.data.rest.webmvc.support;

import java.util.ArrayList;
import java.util.List;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Jon Brisbin
 */
public class ConstraintViolationExceptionMessage {

  private final ConstraintViolationException cve;
  private final List<ConstraintViolationMessage> messages = new ArrayList<ConstraintViolationMessage>();

  public ConstraintViolationExceptionMessage(ConstraintViolationException cve) {
    this.cve = cve;
    for(ConstraintViolation cv : cve.getConstraintViolations()) {
      messages.add(new ConstraintViolationMessage(cv));
    }
  }

  @JsonProperty("cause")
  public String getCause() {
    return cve.getMessage();
  }

  @JsonProperty("messages")
  public List<ConstraintViolationMessage> getMessages() {
    return messages;
  }

}
