// src/main/scala/blockchainapp/actors/TransactionManager.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages.{AddTransaction, GetTransactions, GetAndReserveTransactions, ClearTransactions, ReserveAmount, ReleaseAmount, GetAvailableBalance}

class TransactionManager(blockchainActor: ActorRef) extends Actor {
  private var mempool: List[blockchainapp.models.Transaction] = List()

  def receive: Receive = {
    case AddTransaction(tx) =>
      if (!mempool.contains(tx)) {
        mempool = mempool :+ tx
        sender() ! "Transaction added to mempool."
        // Optionally, propagate the transaction to the cluster
      } else {
        sender() ! "Transaction already exists in mempool."
      }
    case GetTransactions =>
      sender() ! mempool
    case GetAndReserveTransactions =>
      sender() ! mempool
    // Implement reservation logic if needed
    case ClearTransactions =>
      mempool = List()
      sender() ! "Mempool cleared."
    case ReserveAmount(senderName, amount) =>
      // Implement reservation logic
      sender() ! "Amount reserved."
    case ReleaseAmount(senderName, amount) =>
      // Implement release logic
      sender() ! "Amount released."
    case GetAvailableBalance(senderName) =>
      // Query blockchainActor for balance
      blockchainActor forward Messages.GetBalance(senderName)
    case _ =>
      println("TransactionManager received unknown message.")
  }
}

object TransactionManager {
  def props(blockchainActor: ActorRef): Props = Props(new TransactionManager(blockchainActor))
}
