// src/main/scala/blockchainapp/models/Transaction.scala

package blockchainapp.models

case class Transaction(
                        sender: String,
                        recipient: String,
                        amount: Double
                      )
