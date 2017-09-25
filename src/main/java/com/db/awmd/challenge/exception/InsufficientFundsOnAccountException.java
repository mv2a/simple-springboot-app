package com.db.awmd.challenge.exception;

public class InsufficientFundsOnAccountException extends RuntimeException {

  public InsufficientFundsOnAccountException(String message) {
    super(message);
  }
}
