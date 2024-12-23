// src/main/scala/blockchainapp/actors/ValidatorActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages._
import blockchainapp.models.Transaction
import akka.pattern.ask
import akka.pattern.pipe
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable

class ValidatorActor(blockchainActor: ActorRef, transactionManager: ActorRef, validationLog: ActorRef) extends Actor {

  implicit val timeout: Timeout = Timeout(5.seconds)
  import context.dispatcher // ExecutionContext for futures

  private val processedTransactions: mutable.Set[String] = mutable.Set()

  def validateDoubleSpend(tx: Transaction): Future[Boolean] = {
    // Create a unique transaction identifier based on sender, recipient, and amount
    val txId = s"${tx.sender}-${tx.recipient}-${tx.amount}"

    if (processedTransactions.contains(txId)) {
      Future.successful(false) // Reject duplicate transaction
    } else {
      // Check available balance considering reserved amounts
      val availableBalanceFuture: Future[Double] = (transactionManager ? GetAvailableBalance(tx.sender)).mapTo[Double]

      availableBalanceFuture.flatMap { availableBalance =>
        if (availableBalance >= tx.amount) {
          // Reserve the amount
          (transactionManager ? ReserveAmount(tx.sender, tx.amount)).mapTo[Boolean].map { reserved =>
            if (reserved) {
              processedTransactions += txId
              true
            } else {
              false
            }
          }
        } else {
          Future.successful(false)
        }
      }
    }
  }

  def receive: Receive = {
    case ValidateTransaction(tx) =>
      if (tx.sender == "coinbase") {
        println(s"Skipping validation for coinbase transaction: $tx")
      } else if (tx.amount <= 0) {
        println(s"Invalid transaction amount: $tx")
        validationLog ! s"Rejected Transaction: $tx (Invalid Amount)"
      } else {
        validateDoubleSpend(tx).map { isValid =>
          if (isValid) {
            transactionManager ! AddTransaction(tx)
            println(s"Transaction validated and added to mempool: $tx")
            validationLog ! s"Accepted Transaction: $tx"
          } else {
            println(s"Transaction rejected (Double-spend attempt or insufficient balance): $tx")
            validationLog ! s"Rejected Transaction: $tx (Double-spend attempt or insufficient balance)"
          }
        }
      }

    case _ =>
      println("ValidatorActor received unknown message.")
  }
}

object ValidatorActor {
  def props(blockchainActor: ActorRef, transactionManager: ActorRef, validationLog: ActorRef): Props =
    Props(new ValidatorActor(blockchainActor, transactionManager, validationLog))
}
