package com.example.grpc;

/** OneOf type for method */
public sealed interface PaymentMethodMethod
    permits PaymentMethodMethod.CreditCardValue,
        PaymentMethodMethod.BankTransferValue,
        PaymentMethodMethod.WalletValue {
  record BankTransferValue(BankTransfer bankTransfer) implements PaymentMethodMethod {
    public BankTransferValue withBankTransfer(BankTransfer bankTransfer) {
      return new BankTransferValue(bankTransfer);
    }
  }

  record CreditCardValue(CreditCard creditCard) implements PaymentMethodMethod {
    public CreditCardValue withCreditCard(CreditCard creditCard) {
      return new CreditCardValue(creditCard);
    }
  }

  record WalletValue(Wallet wallet) implements PaymentMethodMethod {
    public WalletValue withWallet(Wallet wallet) {
      return new WalletValue(wallet);
    }
  }
}
