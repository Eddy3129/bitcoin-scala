// src/main/scala/blockchainapp/actors/MiningActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.event.Logging
import blockchainapp.actors.Messages._
import blockchainapp.models.{Block, Transaction}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable

class MiningActor(
                   transactionManager: ActorRef,
                   blockchainActor: ActorRef,
                   difficulty: Int,
                   minerReward: Double,
                   minerName: String,
                   miningLog: ActorRef
                 ) extends Actor {

  implicit val timeout: Timeout = Timeout(5.seconds)
  import context.dispatcher // ExecutionContext for futures

  // Initialize Akka's built-in logger
  val log = Logging(context.system, this)

  def receive: Receive = {
    case StartMining =>
      val transactionsFuture: Future[List[Transaction]] = (transactionManager ? GetAndReserveTransactions).mapTo[List[Transaction]]

      transactionsFuture.map { transactions =>
        log.info(s"MiningActor $minerName received transactions: ${transactions.mkString(", ")}")
        if (transactions.nonEmpty) {
          miningLog ! s"Mining started by $minerName with transactions: ${transactions.mkString(", ")}"
          // Add coinbase transaction for miner reward
          val minerRewardTx = Transaction("coinbase", minerName, minerReward)
          val allTransactions = transactions :+ minerRewardTx
          blockchainActor ! MineBlock(allTransactions, minerName)
          // miningLog ! s"Mining completed by $minerName. Reward: $$${minerReward}"
          // Release reserved balances for the processed transactions
          transactions.foreach { tx =>
            transactionManager ! ReleaseAmount(tx.sender, tx.amount)
          }
        } else {
          miningLog ! s"$minerName status: Idle"
        }
      }.recover {
        case ex =>
          log.error(s"Mining failed for $minerName: ${ex.getMessage}")
          miningLog ! s"Mining failed by $minerName: ${ex.getMessage}"
      }

    case "Block mined successfully." =>
      miningLog ! s"Mining completed by $minerName. Reward credited."
    // Re-enable the mine button or perform other UI-related actions if necessary
    // This can be handled via messages to the frontend if needed

    case _ =>
      // Handle other unknown messages gracefully
      log.warning(s"MiningActor $minerName received an unknown message: ${sender().path}")
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
           ): Props =
    Props(new MiningActor(transactionManager, blockchainActor, difficulty, minerReward, minerName, miningLog))
}
