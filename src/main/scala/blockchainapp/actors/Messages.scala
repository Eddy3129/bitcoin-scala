// src/main/scala/blockchainapp/actors/Messages.scala

package blockchainapp.actors

import akka.actor.ActorRef
import blockchainapp.models.{Block, Transaction, Account}

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
  case class BlockchainSubscribe(subscriber: ActorRef)
  case class BlockchainUnsubscribe(subscriber: ActorRef)
  case class BlockchainUpdated(chain: List[Block])

  // Messages for Distributed System
  case class PropagateTransaction(tx: Transaction)
  case class PropagateBlock(block: Block)
  case class ValidateBlockRequest(block: Block)
  case class ValidateBlockResponse(isValid: Boolean, reason: String)
  case object ValidateLatestBlock
  case class NetworkState(mempool: List[Transaction], chain: List[Block])
  case object RequestNetworkState
  case class NetworkStateResponse(state: NetworkState)
  case class SyncRequest(fromBlock: Int)
  case class SyncResponse(blocks: List[Block])

  // Additional Messages
  case object BlockMinedSuccessfully
}
