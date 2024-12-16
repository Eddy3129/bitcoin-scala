import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

// Mining Actor - Handles mining with proof of work
class MiningActor(transactionManager: ActorRef, difficulty: Int) extends Actor {
  implicit val timeout: Timeout = Timeout(5.seconds)

  def receive: Receive = {
    case "mine" =>
      (transactionManager ? "getTransactions").mapTo[List[Transaction]].foreach { transactions =>
        if (transactions.nonEmpty) {
          val blockchainActor = context.actorSelection("/user/blockchainActor")
          blockchainActor ! MineBlock(transactions)
        } else {
          println("No transactions to mine.")
        }
      }
  }
}

// Mining Block Message
case class MineBlock(transactions: List[Transaction])
