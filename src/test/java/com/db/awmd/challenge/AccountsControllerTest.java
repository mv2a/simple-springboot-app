package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.InsufficientFundsOnAccountException;
import com.db.awmd.challenge.exception.InvalidTransferAmountException;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class AccountsControllerTest {

  private MockMvc mockMvc;

  @Mock
  private NotificationService notificationService;

  @Autowired
  @InjectMocks
  private AccountsService accountsService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Before
  public void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  public void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
      .andExpect(status().isOk())
      .andExpect(
        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }

  @Test(expected = AccountNotFoundException.class)
  public void transferAmountNonExistentAccount() throws Exception {
    Account account = new Account("Id-123", new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.accountsService.transferAmount("Id-123", "Id-002", new BigDecimal("0"));
  }

  @Test(expected = InsufficientFundsOnAccountException.class)
  public void transferAmountInsufficientFunds() {
    Account account = new Account("Id-123", new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    Account account2 = new Account("Id-002", new BigDecimal("0"));
    this.accountsService.createAccount(account2);
    this.accountsService.transferAmount("Id-123", "Id-002", new BigDecimal("500"));
  }

  @Test(expected = InvalidTransferAmountException.class)
  public void transferInvalidAmount() {
    Account account = new Account("Id-123", new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    Account account2 = new Account("Id-002", new BigDecimal("0"));
    this.accountsService.createAccount(account2);
    this.accountsService.transferAmount("Id-123", "Id-002", new BigDecimal("-100"));
  }

  @Test
  public void transferAmountBetweenAccounts() {
    Account accountFrom = new Account("Id-123", new BigDecimal("100"));
    this.accountsService.createAccount(accountFrom);
    Account accountTo = new Account("Id-002", new BigDecimal("100"));
    this.accountsService.createAccount(accountTo);
    this.accountsService.transferAmount("Id-123", "Id-002", new BigDecimal("100"));

    assertThat(accountFrom.getBalance()).isEqualTo(new BigDecimal("0"));
    assertThat(accountTo.getBalance()).isEqualTo(new BigDecimal("200"));

    verify(notificationService, times(2))
            .notifyAboutTransfer(Mockito.any(Account.class),  Mockito.anyString());

    verify(notificationService, times(1))
            .notifyAboutTransfer(accountFrom, "Transferred 100 to account: Id-002");

    verify(notificationService, times(1))
              .notifyAboutTransfer(accountTo, "Received 100 from account: Id-123");
  }

  @Test
  public void transferAmountEndPoint() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
            .content("{\"accountId\":\"Id-002\",\"balance\":100}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts/transfer/Id-123/Id-002").contentType(MediaType.APPLICATION_JSON)
            .content("300"))
            .andExpect(status().isAccepted());

    this.mockMvc.perform(get("/v1/accounts/Id-123"))
            .andExpect(
                    content().string("{\"accountId\":\"Id-123\",\"balance\":700}"));

    this.mockMvc.perform(get("/v1/accounts/Id-002"))
            .andExpect(
                    content().string("{\"accountId\":\"Id-002\",\"balance\":400}"));
    }
}
