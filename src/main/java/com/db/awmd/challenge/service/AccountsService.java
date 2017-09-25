package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.InsufficientFundsOnAccountException;
import com.db.awmd.challenge.exception.InvalidTransferAmountException;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.Synchronized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  public AccountsService(AccountsRepository accountsRepository) {
    this.accountsRepository = accountsRepository;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  @Synchronized
  public void transferAmount(String accountIdFrom, String accountIdTo, BigDecimal amountToTransfer) {
    Account accountFrom  = this.accountsRepository.getAccount(accountIdFrom);
    Account accountTo  = this.accountsRepository.getAccount(accountIdTo);
    if (accountFrom == null || accountTo == null){
        throw new AccountNotFoundException("Account not found");
    }
    if (amountToTransfer.compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidTransferAmountException("Amount to be transferred must be greater than 0");
    }
    if (accountFrom.getBalance().compareTo(amountToTransfer) == -1) {
        throw new InsufficientFundsOnAccountException("Insufficient funds on origin account");
    }

    accountFrom.setBalance(accountFrom.getBalance().subtract(amountToTransfer));
    accountTo.setBalance(accountTo.getBalance().add(amountToTransfer));

    this.notificationService.notifyAboutTransfer(accountFrom, "Transferred "
            + amountToTransfer.toPlainString() + " to account: " + accountIdTo);

    this.notificationService.notifyAboutTransfer(accountTo, "Received "
            + amountToTransfer.toPlainString() + " from account: " + accountIdFrom);
  }
}
