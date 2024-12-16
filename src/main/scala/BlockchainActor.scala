import akka.actor.{Actor, ActorRef}

// Blockchain Actor - Manages the chain
class BlockchainActor(difficulty: Int) extends Actor {
  private var chain: List[Block] = List(Block(0, "0", List(), System.currentTimeMillis(), 0, "0"))
  private var pendingTransactions: List[Transaction] = List()

  def receive: Receive = {
    case MineBlock(transactions) =>
      println(s"Mining block with transactions: ${transactions.mkString(", ")}")
      val index = chain.size
      val previousBlock = chain.last
      val newBlock = Block.createBlock(index, previousBlock.currentHash, transactions, difficulty)

      // Add new block to chain
      chain = chain :+ newBlock
      pendingTransactions = List() // Clear pending transactions
      println(s"Block mined: ${newBlock.currentHash}, Transactions: ${transactions.mkString(", ")}")

    case "getChain" =>
      sender() ! chain

    case "isValid" =>
      sender() ! isValid
  }

  // Simple validation of the blockchain (checks previous hash links)
  def isValid: Boolean = {
    chain.zip(chain.tail).forall { case (previous, current) =>
      current.previousHash == previous.currentHash
    }
  }
}
