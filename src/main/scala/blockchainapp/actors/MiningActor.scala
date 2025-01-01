// src/main/scala/blockchainapp/actors/MiningActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages.{StartMining, MineBlock}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Success, Failure}

class MiningActor(
                   transactionManager: ActorRef,
                   blockchainActor: ActorRef,
                   difficulty: Int,
                   minerReward: Double,
                   minerName: String,
                   miningLog: ActorRef
                 ) extends Actor {

  // Import the dispatcher for Futures
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case StartMining =>
      miningLog ! s"MiningActor $minerName received StartMining"

      // Use the ask pattern to request transactions from the TransactionManager
      val transactionsFuture: Future[List[blockchainapp.models.Transaction]] =
        (transactionManager ? Messages.GetAndReserveTransactions).mapTo[List[blockchainapp.models.Transaction]]

      transactionsFuture.onComplete {
        case Success(transactions) =>
          if (transactions.nonEmpty) {
            miningLog ! s"MiningActor $minerName started mining with transactions: ${transactions.mkString(", ")}"
            // Send the MineBlock message to BlockchainActor
            blockchainActor ! MineBlock(transactions, minerName)
          } else {
            miningLog ! s"MiningActor $minerName found no transactions to mine."
          }
        case Failure(exception) =>
          miningLog ! s"MiningActor $minerName failed to retrieve transactions: ${exception.getMessage}"
      }

    case _ =>
      miningLog ! s"MiningActor $minerName received unknown message."
  }
}

object MiningActor {
  def props(
             transactionManager: ActorRef,
             blockchainActor: ActorRef,
             difficulty: Int,
             minerReward: Double,
             minerName: String,
             miningLog: ActorRef
           ): Props = Props(new MiningActor(transactionManager, blockchainActor, difficulty, minerReward, minerName, miningLog))
}
