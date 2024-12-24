// src/main/scala/blockchainapp/actors/ValidatorActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages.{ValidateTransaction, AddTransaction, ValidationLogUpdate, GetBalance}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}

class ValidatorActor(
                      blockchainActor: ActorRef,
                      transactionManager: ActorRef,
                      validationLog: ActorRef
                    ) extends Actor {

  // Import the dispatcher for Futures
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case ValidateTransaction(tx) =>
      // Use the ask pattern to request the sender's balance from BlockchainActor
      val balanceFuture: Future[Double] =
        (blockchainActor ? GetBalance(tx.sender)).mapTo[Double]

      balanceFuture.onComplete {
        case Success(balance) =>
          if (balance >= tx.amount || tx.sender == "coinbase") {
            validationLog ! s"Accepted Transaction: $tx"
            transactionManager ! AddTransaction(tx)
          } else {
            validationLog ! s"Rejected Transaction: $tx - Insufficient balance."
          }
        case Failure(exception) =>
          validationLog ! s"Rejected Transaction: $tx - Error fetching balance: ${exception.getMessage}"
      }

    case msg =>
      println(s"ValidatorActor received unknown message: $msg")
  }
}

object ValidatorActor {
  def props(
             blockchainActor: ActorRef,
             transactionManager: ActorRef,
             validationLog: ActorRef
           ): Props = Props(new ValidatorActor(blockchainActor, transactionManager, validationLog))
}
