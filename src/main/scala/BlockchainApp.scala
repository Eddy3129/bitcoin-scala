import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.StdIn

object BlockchainApp extends App {
  val system = ActorSystem("BlockchainSystem")

  val difficulty = 2
  val blockchainActor = system.actorOf(Props(new BlockchainActor(difficulty)), "blockchainActor")
  val transactionManager = system.actorOf(Props(new TransactionManager), "transactionManager")
  val miningActor = system.actorOf(Props(new MiningActor(transactionManager, difficulty)), "miningActor")

  // Display options and interact with the user
  println("Welcome to the Blockchain System!")
  var running = true

  while (running) {
    println("\nChoose an option:")
    println("1. Add a new transaction")
    println("2. Mine a new block")
    println("3. View blockchain")
    println("4. Check if blockchain is valid")
    println("5. Exit")

    StdIn.readLine() match {
      case "1" =>
        // Add a new transaction
        println("Enter sender name:")
        val sender = StdIn.readLine()
        println("Enter recipient name:")
        val recipient = StdIn.readLine()
        println("Enter transaction amount:")
        val amount = StdIn.readDouble()

        transactionManager ! Transaction(sender, recipient, amount)
        println(s"Transaction from $sender to $recipient of amount $amount added.")

      case "2" =>
        // Mine a new block
        miningActor ! "mine"
        println("Mining a new block...")

      case "3" =>
        // View the blockchain
        implicit val timeout: Timeout = Timeout(5.seconds) // Define the implicit Timeout here
        (blockchainActor ? "getChain").mapTo[List[Block]].foreach { chain =>
          println("\nBlockchain:")
          chain.drop(1).foreach(block =>
            println(s"Block ${block.index}: Hash: ${block.currentHash}, Transactions: ${block.transactions.mkString(", ")}")
          )
        }

      case "4" =>
        // Check if the blockchain is valid
        implicit val timeout: Timeout = Timeout(5.seconds) // Define the implicit Timeout here as well
        (blockchainActor ? "isValid").mapTo[Boolean].foreach { isValid =>
          println(s"Is the blockchain valid? $isValid")
        }

      case "5" =>
        // Exit the system
        println("Exiting Blockchain System...")
        running = false

      case _ =>
        println("Invalid option. Please choose again.")
    }
  }

  system.terminate()
}
