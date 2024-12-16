import akka.actor.Actor

// Transaction Manager Actor - Manages incoming transactions
class TransactionManager extends Actor {
  private var transactions: List[Transaction] = List()

  def receive: Receive = {
    case t: Transaction =>
      transactions = transactions :+ t
      println(s"Transaction received: $t")

    case "getTransactions" =>
      sender() ! transactions
  }
}
