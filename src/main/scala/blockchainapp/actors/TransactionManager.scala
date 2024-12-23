// src/main/scala/blockchainapp/actors/TransactionManager.scala

package blockchainapp.actors

import akka.actor.{Actor, Props}
import Messages._
import blockchainapp.models.Transaction
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class TransactionManager(blockchainActor: akka.actor.ActorRef) extends Actor {

  import context.dispatcher // ExecutionContext for futures

  private var transactions: List[Transaction] = List()
  private var reservedBalances: Map[String, Double] = Map().withDefaultValue(0.0)

  def receive: Receive = {
    case AddTransaction(tx) =>
      transactions = transactions :+ tx
      reservedBalances += tx.sender -> (reservedBalances(tx.sender) + tx.amount)
      println(s"Transaction added to mempool: $tx")

    case GetTransactions =>
      sender() ! transactions

    case GetAndReserveTransactions =>
      val reservedTransactions = transactions
      transactions = List() // Clear mempool
      println(s"Reserved transactions for mining: ${reservedTransactions.mkString(", ")}")
      sender() ! reservedTransactions

    case ClearTransactions =>
      transactions = List()
      reservedBalances = Map().withDefaultValue(0.0)
      println("Mempool and reserved balances cleared.")

    case GetAvailableBalance(senderName) =>
      implicit val timeout: Timeout = Timeout(5.seconds)
      val balanceFuture: Future[Double] = (blockchainActor ? GetBalance(senderName)).mapTo[Double]
      val availableBalance = balanceFuture.map { balance =>
        balance - reservedBalances(senderName)
      }
      availableBalance.pipeTo(sender())

    case ReserveAmount(senderName, amount) =>
      implicit val timeout: Timeout = Timeout(5.seconds)
      val balanceFuture: Future[Double] = (blockchainActor ? GetBalance(senderName)).mapTo[Double]
      val reservationResult: Future[Boolean] = balanceFuture.map { balance =>
        if (balance >= (reservedBalances(senderName) + amount)) {
          reservedBalances += senderName -> (reservedBalances(senderName) + amount)
          true
        } else {
          false
        }
      }
      reservationResult.pipeTo(sender())

    case ReleaseAmount(senderName, amount) =>
      val currentReserved = reservedBalances(senderName)
      val newReserved = math.max(currentReserved - amount, 0.0)
      reservedBalances += senderName -> newReserved
      println(s"Released $$${amount} from $senderName's reserved balance.")

    case _ =>
      println("TransactionManager received unknown message.")
  }
}

object TransactionManager {
  def props(blockchainActor: akka.actor.ActorRef): Props = Props(new TransactionManager(blockchainActor))
}
