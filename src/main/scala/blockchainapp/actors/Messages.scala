// src/main/scala/blockchainapp/actors/Messages.scala

package blockchainapp.actors

import akka.actor.ActorRef
import blockchainapp.models.{Block, Transaction}

object Messages {

  // Messages for BlockchainActor
  case class MineBlock(transactions: List[Transaction], minerName: String)
  case object GetChain
  case class GetAccounts()
  case class CreateAccount(username: String)
  case class UpdateBalance(accountName: String, amount: Double)
  case class GetBalance(accountName: String)
  case class ReceiveBlock(block: Block)
  case class RequestChain(replyTo: ActorRef)
  case class RespondChain(chain: List[Block])

  // Messages for TransactionManager
  case class AddTransaction(tx: Transaction)
  case object GetTransactions
  case object GetAndReserveTransactions
  case object ClearTransactions
  case class GetAvailableBalance(sender: String)
  case class ReserveAmount(sender: String, amount: Double)
  case class ReleaseAmount(sender: String, amount: Double)

  // Messages for ValidatorActor
  case class ValidateTransaction(tx: Transaction)

  // Messages for MiningActor
  case object StartMining
  case object SimulateAttack

  // Messages for LoggingActor
  case class ValidationLogUpdate(log: String)
  case class MiningLogUpdate(log: String)
  case object GetLogs

  // Messages for BlockchainPublisher
  case class Subscribe(subscriber: ActorRef)
  case class Unsubscribe(subscriber: ActorRef)
  case class BlockchainUpdated(chain: List[Block])

  // Additional messages for Block propagation
  // (Already included above)
}
