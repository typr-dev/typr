package com.example.grpc



/** OneOf type for method */
sealed interface PaymentMethodMethod {
  data class BankTransferValue(val bankTransfer: BankTransfer) : PaymentMethodMethod

  data class CreditCardValue(val creditCard: CreditCard) : PaymentMethodMethod

  data class WalletValue(val wallet: Wallet) : PaymentMethodMethod
}