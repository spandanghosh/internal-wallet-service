package com.dinoventures.wallet.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(long id) {
        super("Account not found: id=" + id);
    }

    public AccountNotFoundException(String name) {
        super("Account not found: name='" + name + "'");
    }
}
