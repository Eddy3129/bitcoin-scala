// src/main/scala/blockchainapp/actors/BlockchainActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages._
import blockchainapp.models.{Account, Block, Transaction}
import scala.collection.mutable
import scala.concurrent.Future
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish

class BlockchainActor(
                       difficulty: Int,
                       minerReward: Double,
                       blockchainPublisher: ActorRef,
                       clusterListener: ActorRef
                     ) extends Actor {

  import context.dispatcher // ExecutionContext for Futures

  private var chain: List[Block] = List(Block.genesis)
  private var accounts: mutable.Map[String, Account] = mutable.Map().withDefaultValue(Account("system", 0.0)) // Initialize with system account

  // Distributed PubSub
  // val mediator: ActorRef = DistributedPubSub(context.system).mediator
  // val blockTopic = "blocks"

  def receive: Receive = {
    case MineBlock(transactions, minerName) =>
      // Asynchronous mining process
      val senderRef = sender()
      val miningFuture: Future[(Block, List[Transaction])] = Future {
        // Validate transactions again to ensure integrity
        val validTransactions = transactions.filter { tx =>
          if (tx.sender == "coinbase") {
            true
          } else {
            accounts.get(tx.sender) match {
              case Some(account) => account.balance >= tx.amount
              case None          => false
            }
          }
        }

        // Add coinbase transaction for miner reward if not already present
        val hasCoinbase = validTransactions.exists(_.sender == "coinbase")
        val allTransactions = if (!hasCoinbase) {
          val minerRewardTx = Transaction("coinbase", minerName, minerReward)
          validTransactions :+ minerRewardTx
        } else {
          validTransactions
        }

        // Create new block
        val newBlock = Block.createBlock(
          index = chain.length,
          previousHash = chain.last.currentHash,
          transactions = allTransactions,
          difficulty = difficulty
        )

        (newBlock, validTransactions)
      }

      miningFuture.map { case (newBlock, validTransactions) =>
        // Update balances
        newBlock.transactions.foreach { tx =>
          if (tx.sender != "coinbase") {
            accounts.get(tx.sender) match {
              case Some(senderAcc) =>
                accounts.update(tx.sender, senderAcc.copy(balance = senderAcc.balance - tx.amount))
              case None =>
                // Handle non-existing sender account if necessary
                println(s"Sender account ${tx.sender} does not exist.")
            }
          }
          accounts.get(tx.recipient) match {
            case Some(recipientAcc) =>
              accounts.update(recipientAcc.name, recipientAcc.copy(balance = recipientAcc.balance + tx.amount))
            case None =>
              // If recipient does not exist, create the account
              accounts += tx.recipient -> Account(tx.recipient, tx.amount)
          }
        }

        // Add block to chain
        chain = chain :+ newBlock
        println(s"Block mined: ${newBlock.currentHash}, Transactions: ${validTransactions.mkString(", ")}")
        blockchainPublisher ! BlockchainUpdated(chain)

        // Remove the following line to eliminate sending ReceiveBlock via DistributedPubSubMediator
        // mediator ! Publish(blockTopic, ReceiveBlock(newBlock))

        // Respond to sender
        senderRef ! "Block mined successfully."
      }.recover {
        case ex =>
          println(s"Mining failed: ${ex.getMessage}")
          senderRef ! "Mining failed."
      }

    case ReceiveBlock(block) =>
      if (isValidBlock(block, chain.last)) {
        // Add block to chain
        chain = chain :+ block
        // Update balances
        block.transactions.foreach { tx =>
          if (tx.sender != "coinbase") {
            accounts.get(tx.sender) match {
              case Some(senderAcc) =>
                accounts.update(tx.sender, senderAcc.copy(balance = senderAcc.balance - tx.amount))
              case None =>
                println(s"Sender account ${tx.sender} does not exist.")
            }
          }
          accounts.get(tx.recipient) match {
            case Some(recipientAcc) =>
              accounts.update(recipientAcc.name, recipientAcc.copy(balance = recipientAcc.balance + tx.amount))
            case None =>
              accounts += tx.recipient -> Account(tx.recipient, tx.amount)
          }
        }
        println(s"Received and added block: ${block.currentHash}")
        blockchainPublisher ! BlockchainUpdated(chain)
      } else {
        println(s"Received invalid block: ${block.currentHash}")
        // Optionally, request the full chain from the sender
      }

    case CreateAccount(username) =>
      if (accounts.contains(username)) {
        sender() ! false
      } else {
        accounts += username -> Account(username, 0.0)
        sender() ! true
      }

    case UpdateBalance(accountName, amount) =>
      accounts.get(accountName) match {
        case Some(account) =>
          accounts.update(accountName, account.copy(balance = account.balance + amount))
          sender() ! true
        case None =>
          sender() ! false
      }

    case GetBalance(accountName) =>
      sender() ! accounts.get(accountName).map(_.balance).getOrElse(0.0)

    case GetAccounts() =>
      sender() ! accounts.values.toList

    case GetChain =>
      sender() ! chain

    case RequestChain(replyTo) =>
      replyTo ! RespondChain(chain)

    case _ =>
      println("BlockchainActor received unknown message.")
  }

  // Helper method to validate a received block
  def isValidBlock(block: Block, previousBlock: Block): Boolean = {
    block.previousHash == previousBlock.currentHash &&
      Block.calculateHash(s"${block.index}${block.previousHash}${block.transactions.mkString("")}${block.nonce}") == block.currentHash &&
      block.currentHash.startsWith("0" * difficulty)
  }
}

object BlockchainActor {
  def props(
             difficulty: Int,
             minerReward: Double,
             blockchainPublisher: ActorRef,
             clusterListener: ActorRef
           ): Props =
    Props(new BlockchainActor(difficulty, minerReward, blockchainPublisher, clusterListener))
}
