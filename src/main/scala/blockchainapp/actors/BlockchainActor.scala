// src/main/scala/blockchainapp/actors/BlockchainActor.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import Messages._
import blockchainapp.models.{Account, Block, Transaction}
import scala.collection.mutable
import scala.concurrent.Future
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import scala.concurrent.duration._
import akka.pattern.pipe

class BlockchainActor(
                       difficulty: Int,
                       minerReward: Double,
                       blockchainPublisher: ActorRef,
                       clusterListener: ActorRef
                     ) extends Actor {

  import context.dispatcher

  private var chain: List[Block] = List(Block.genesis)
  private var accounts: mutable.Map[String, Account] = mutable.Map().withDefaultValue(Account("system", 0.0))
  private var mempool: List[Transaction] = List()

  // Distributed PubSub for mempool and blocks
  val mediator: ActorRef = DistributedPubSub(context.system).mediator
  val mempoolTopic = "mempool"
  val blocksTopic = "blocks"
  val networkStateTopic = "network-state"

  mediator ! Subscribe(blocksTopic, self)
  mediator ! Subscribe(mempoolTopic, self)
  mediator ! Subscribe(networkStateTopic, self)

  def validateBlock(block: Block): (Boolean, String) = {
    if (block.index != chain.length) {
      return (false, s"Invalid block index: ${block.index}, expected: ${chain.length}")
    }

    if (block.previousHash != chain.last.currentHash) {
      return (false, s"Invalid previous hash: ${block.previousHash}, expected: ${chain.last.currentHash}")
    }

    val calculatedHash = Block.calculateHash(
      s"${block.index}${block.previousHash}${block.transactions.mkString}${block.nonce}"
    )

    if (calculatedHash != block.currentHash) {
      return (false, s"Invalid hash: ${block.currentHash}, calculated: $calculatedHash")
    }

    if (!block.currentHash.startsWith("0" * difficulty)) {
      return (false, s"Hash doesn't meet difficulty requirement")
    }

    // Validate all transactions in block
    val validTransactions = block.transactions.forall { tx =>
      if (tx.sender == "coinbase") true
      else {
        val senderBalance = accounts.get(tx.sender).map(_.balance).getOrElse(0.0)
        senderBalance >= tx.amount
      }
    }

    if (!validTransactions) {
      return (false, "One or more transactions are invalid")
    }

    (true, "Block is valid")
  }

  private def processBlock(block: Block): Unit = {
    // Update account balances based on block transactions
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
          accounts.update(tx.recipient, recipientAcc.copy(balance = recipientAcc.balance + tx.amount))
        case None =>
          accounts += tx.recipient -> Account(tx.recipient, tx.amount)
      }
    }

    // Remove processed transactions from mempool
    mempool = mempool.filterNot(tx =>
      block.transactions.exists(blockTx =>
        blockTx.sender == tx.sender &&
          blockTx.recipient == tx.recipient &&
          blockTx.amount == tx.amount
      )
    )

    // Notify subscribers about the update
    blockchainPublisher ! BlockchainUpdated(chain)
  }

  def isValidChain(receivedChain: List[Block]): Boolean = {
    receivedChain.foldLeft((true, "")) { (acc, block) =>
      val (isValidSoFar, _) = acc
      if (!isValidSoFar) acc
      else validateBlock(block)
    }._1
  }

  def filterMempool(receivedChain: List[Block]): List[Transaction] = {
    // Remove transactions that are already included in the received chain
    receivedChain.flatMap(_.transactions).foldLeft(mempool) { (acc, tx) =>
      acc.filterNot(existingTx => existingTx == tx)
    }
  }

  def receive: Receive = {
    case MineBlock(transactions, minerName) =>
      val senderRef = sender()
      val miningFuture: Future[(Block, List[Transaction])] = Future {
        val validTransactions = transactions.filter { tx =>
          if (tx.sender == "coinbase") {
            true
          } else {
            accounts.get(tx.sender) match {
              case Some(account) => account.balance >= tx.amount
              case None => false
            }
          }
        }

        val hasCoinbase = validTransactions.exists(_.sender == "coinbase")
        val allTransactions = if (!hasCoinbase) {
          val minerRewardTx = Transaction("coinbase", minerName, minerReward)
          validTransactions :+ minerRewardTx
        } else {
          validTransactions
        }

        val newBlock = Block.createBlock(
          index = chain.length,
          previousHash = chain.last.currentHash,
          transactions = allTransactions,
          difficulty = difficulty
        )

        (newBlock, validTransactions)
      }

      miningFuture.map { case (newBlock, validTransactions) =>
        val (isValid, reason) = validateBlock(newBlock)
        if (isValid) {
          chain = chain :+ newBlock
          processBlock(newBlock)
          // Propagate the new block to the network
          mediator ! Publish(blocksTopic, newBlock)
          senderRef ! BlockMinedSuccessfully
        } else {
          senderRef ! s"Block validation failed: $reason"
        }
      }.recover {
        case ex =>
          println(s"Mining failed: ${ex.getMessage}")
          senderRef ! "Mining failed."
      }

    case PropagateTransaction(tx) =>
      if (!mempool.exists(t => t.sender == tx.sender && t.recipient == tx.recipient && t.amount == tx.amount)) {
        mempool = mempool :+ tx
        println(s"Propagated transaction added to mempool: $tx")
        // Optionally, notify the BlockchainPublisher
        blockchainPublisher ! BlockchainUpdated(chain)
      }

    case PropagateBlock(block) =>
      val (isValid, reason) = validateBlock(block)
      if (isValid) {
        chain = chain :+ block
        processBlock(block)
        mediator ! Publish(blocksTopic, block) // Further propagate if necessary
      } else {
        println(s"Received invalid block: $reason")
        sender() ! ValidateBlockResponse(false, reason)
      }

    case ValidateLatestBlock =>
      if (chain.length > 1) {
        val latestBlock = chain.last
        val (isValid, reason) = validateBlock(latestBlock)
        sender() ! ValidateBlockResponse(isValid, reason)
      } else {
        sender() ! ValidateBlockResponse(true, "Genesis block is always valid")
      }

    case RequestNetworkState =>
      sender() ! NetworkState(mempool, chain)

    case SyncRequest(fromBlock) =>
      val blocksToSync = chain.drop(fromBlock)
      sender() ! SyncResponse(blocksToSync)

    case RequestChain =>
      sender() ! RespondChain(chain)

    case RespondChain(receivedChain) =>
      if (receivedChain.length > chain.length && isValidChain(receivedChain)) {
        chain = receivedChain
        mempool = filterMempool(receivedChain)
        blockchainPublisher ! BlockchainUpdated(chain)
        println("Blockchain synchronized with received chain.")
      }

    // Existing message handlers
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

    // Handle SubscribeAck messages
    case SubscribeAck(Subscribe(`blocksTopic`, None, `self`)) =>
      println(s"BlockchainActor successfully subscribed to $blocksTopic")
    case SubscribeAck(Subscribe(`mempoolTopic`, None, `self`)) =>
      println(s"BlockchainActor successfully subscribed to $mempoolTopic")
    case SubscribeAck(Subscribe(`networkStateTopic`, None, `self`)) =>
      println(s"BlockchainActor successfully subscribed to $networkStateTopic")

    case msg =>
      println(s"BlockchainActor received unknown message: $msg")
  }
}

object BlockchainActor {
  def props(
             difficulty: Int,
             minerReward: Double,
             blockchainPublisher: ActorRef,
             clusterListener: ActorRef
           ): Props = Props(new BlockchainActor(difficulty, minerReward, blockchainPublisher, clusterListener))
}
