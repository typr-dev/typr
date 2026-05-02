package com.example.grpc



/** OneOf type for method */
sealed trait PaymentMethodMethod

object PaymentMethodMethod {
  case class BankTransferValue(bankTransfer: BankTransfer) extends PaymentMethodMethod

  case class CreditCardValue(creditCard: CreditCard) extends PaymentMethodMethod

  case class WalletValue(wallet: Wallet) extends PaymentMethodMethod
}