package io.lfdt.paladin.sdk;

public class PaladinException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PaladinException(String message) {
    super(message);
  }

  public PaladinException(String message, Throwable cause) {
    super(message, cause);
  }
}
