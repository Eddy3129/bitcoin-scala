// src/main/scala/blockchainapp/controllers/MainController.scala

package blockchainapp.controllers

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import blockchainapp.ActorSystemProvider
import blockchainapp.actors._
import blockchainapp.actors.Messages._
import blockchainapp.models.{Account, Block, Transaction}
import javafx.fxml.FXML
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.collections.ObservableBuffer
import scalafx.scene.control._
import scalafxml.core.macros.sfxml

import scala.collection.convert.ImplicitConversions.`collection AsScalaIterable`
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

// Import the global ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global

@sfxml
class MainController(
                      @FXML private val usernameField: TextField,
                      @FXML private val createAccountButton: Button,
                      @FXML private val senderComboBox: ComboBox[String],
                      @FXML private val recipientComboBox: ComboBox[String],
                      @FXML private val amountField: TextField,
                      @FXML private val addButton: Button,
                      @FXML private val mineButton: Button,
                      @FXML private val simulateAttackButton: Button,
                      @FXML private val startMinerButton: Button,
                      @FXML private val statusLabel: Label,
                      @FXML private val mempoolListView: ListView[String],
                      @FXML private val blockchainListView: ListView[String],
                      @FXML private val accountsListView: ListView[String],
                      @FXML private val validationListView: ListView[String],
                      @FXML private val miningListView: ListView[String]
                    ) extends Serializable {

  // Initialize Akka system and actors
  private val system: ActorSystem = ActorSystemProvider.system
  private val difficulty: Int = 3 // Adjust as needed
  private val minerReward: Double = 50.0
  private val initialBalance: Double = 100.0 // Define initial balance for new accounts

  // Create publisher
  private val blockchainPublisher: ActorRef = system.actorOf(BlockchainPublisher.props, "blockchainPublisher")

  // Create log actors for validation and mining
  private val validationLog: ActorRef = system.actorOf(LoggingActor.props("validation"), "validationLog")
  private val miningLog: ActorRef = system.actorOf(LoggingActor.props("mining"), "miningLog")

  // Create Cluster Listener
  private val clusterListener: ActorRef = system.actorOf(ClusterListener.props(blockchainPublisher), "clusterListener")

  // Initialize main actors
  private val blockchainActor: ActorRef = system.actorOf(BlockchainActor.props(difficulty, minerReward, blockchainPublisher, clusterListener), "blockchainActor")
  private val transactionManager: ActorRef = system.actorOf(TransactionManager.props(blockchainActor), "transactionManager") // Pass blockchainActor

  // Initialize multiple validators
  private val validatorActors: List[ActorRef] = List(
    system.actorOf(ValidatorActor.props(blockchainActor, transactionManager, validationLog), "validator1"),
    system.actorOf(ValidatorActor.props(blockchainActor, transactionManager, validationLog), "validator2"),
    system.actorOf(ValidatorActor.props(blockchainActor, transactionManager, validationLog), "validator3")
  )

  // Initialize multiple miners
  private var miningActors: List[ActorRef] = List(
    system.actorOf(MiningActor.props(transactionManager, blockchainActor, difficulty, minerReward, "miner1", miningLog), "minerActor1"),
    system.actorOf(MiningActor.props(transactionManager, blockchainActor, difficulty, minerReward, "miner2", miningLog), "minerActor2")
  )

  // Round-robin index for validators
  private var currentValidatorIndex = 0

  // Initialize accounts on startup
  initializeListeners()
  fetchAndDisplayAccounts()
  fetchAndDisplayMempool()

  // Initialize ObservableBuffers for logs
  private val validationLogsBuffer = ObservableBuffer[String]()
  private val miningLogsBuffer = ObservableBuffer[String]()

  // Bind the ObservableBuffers to the ListViews
  validationListView.items = validationLogsBuffer
  miningListView.items = ObservableBuffer[String]() // Initialize empty to avoid duplicate binding
  miningListView.items = miningLogsBuffer

  // Create an actor to handle incoming messages (FrontendListener)
  private val frontendListener: ActorRef = system.actorOf(Props(new Actor {
    // Removed unnecessary imports related to DistributedPubSubMediator

    override def preStart(): Unit = {
      // Use Messages.Subscribe to avoid confusion with mediator's Subscribe
      blockchainPublisher ! Messages.Subscribe(self)
      context.system.eventStream.subscribe(self, classOf[ValidationLogUpdate])
      context.system.eventStream.subscribe(self, classOf[MiningLogUpdate])
      context.system.eventStream.subscribe(self, classOf[BlockchainUpdated])

      // Removed DistributedPubSub subscription to eliminate duplicate block entries
      // val mediator = DistributedPubSub(context.system).mediator
      // mediator ! Subscribe("blocks", self)

      println("FrontendListener subscribed to blockchainPublisher and eventStream.")
    }

    override def postStop(): Unit = {
      context.system.eventStream.unsubscribe(self)
      super.postStop()
    }

    def receive: Receive = {
      // Removed handling of ReceiveBlock to prevent duplicate entries
      /*
      case ReceiveBlock(block) =>
        Platform.runLater {
          blockchainListView.items = ObservableBuffer(blockchainListView.items.value :+ s"Block ${block.index} - Hash: ${block.currentHash.take(10)}...")
        }
      */

      case BlockchainUpdated(chain) =>
        Platform.runLater {
          blockchainListView.items = ObservableBuffer(chain.map { block =>
            if (block.index == 0) {
              s"Block ${block.index} - Genesis Block"
            } else {
              val txs = block.transactions.map(tx => s"${tx.sender}->${tx.recipient}:$$${tx.amount}").mkString(", ")
              s"Block ${block.index} - Hash: ${block.currentHash.take(10)}... - Tx: $txs"
            }
          })
        }

      case ValidationLogUpdate(log) =>
        Platform.runLater {
          validationLogsBuffer += log
          // Refresh the mempool and accounts
          if (log.startsWith("Accepted Transaction") || log.startsWith("Rejected Transaction")) {
            fetchAndDisplayMempool()
            fetchAndDisplayAccounts()
          }
        }

      case MiningLogUpdate(log) =>
        Platform.runLater {
          miningLogsBuffer += log
        }

      case "Block mined successfully." =>
        // Handle the successful mining completion
        miningLog ! "Block mined successfully."
        // Re-enable mine button if it was disabled
        Platform.runLater {
          mineButton.disable = false
          statusLabel.text = "Block mined successfully."
        }

      case _ =>
        println("FrontendListener received unknown message.")
    }
  }), "FrontendListener")

  // Create Account Button Action
  createAccountButton.onAction = handle {
    val username = usernameField.text.value.trim
    if (username.nonEmpty) {
      implicit val timeout: Timeout = Timeout(5.seconds)
      val createAccountFuture = (blockchainActor ? CreateAccount(username)).mapTo[Boolean]
      createAccountFuture.onComplete {
        case Success(success) =>
          if (success) {
            // Automatically fund the account with initial balance
            val fundAccountFuture = (blockchainActor ? UpdateBalance(username, initialBalance)).mapTo[Boolean]
            fundAccountFuture.onComplete {
              case Success(funded) =>
                if (funded) {
                  Platform.runLater {
                    statusLabel.text = s"Account '$username' created and funded with $$${initialBalance} successfully."
                    showInfoDialog("Success", s"Account '$username' created and funded with $$${initialBalance} successfully.")
                    fetchAndDisplayAccounts()
                    usernameField.text = ""
                  }
                } else {
                  Platform.runLater {
                    showErrorDialog("Funding Failed", s"Account '$username' was created but funding failed.")
                  }
                }
              case Failure(exception) =>
                Platform.runLater {
                  showErrorDialog("Funding Error", s"An error occurred while funding the account: ${exception.getMessage}")
                }
            }
          } else {
            Platform.runLater {
              showErrorDialog("Account Creation Failed", s"Account '$username' already exists.")
            }
          }
        case Failure(exception) =>
          Platform.runLater {
            showErrorDialog("Error", s"An error occurred: ${exception.getMessage}")
          }
      }
    } else {
      showErrorDialog("Invalid Input", "Username cannot be empty.")
    }
  }

  // Add Transaction Button Action
  addButton.onAction = handle {
    val senderName = Option(senderComboBox.value.value).getOrElse("")
    val recipientName = Option(recipientComboBox.value.value).getOrElse("")
    val amount = Try(amountField.text.value.toDouble).getOrElse(0.0)

    if (senderName.nonEmpty && recipientName.nonEmpty && amount > 0 && senderName != recipientName) {
      val transaction = Transaction(senderName, recipientName, amount)
      validatorActors(currentValidatorIndex) ! ValidateTransaction(transaction)
      currentValidatorIndex = (currentValidatorIndex + 1) % validatorActors.size
      statusLabel.text = s"Transaction sent for validation: $senderName → $recipientName : $$ $amount"
      amountField.text = ""
    } else {
      showErrorDialog("Invalid Transaction", "Ensure sender, recipient, and valid amount are selected.")
    }
  }

  // Mine Button Action
  mineButton.onAction = handle {
    miningActors.foreach(_ ! StartMining)
    statusLabel.text = "Mining started..."
    // Disable mine button during mining
    mineButton.disable = true
    // Re-enable mine button after mining completes via frontend listener
    // This is handled by the mining log updates and the "Block mined successfully." message
  }

  // Simulate Attack Button Action
  simulateAttackButton.onAction = handle {
    val attacker = "attacker"
    val victim1 = "merchant1"
    val victim2 = "merchant2"
    val attackAmount = 1000.0
    val fundingAmount = 1000.0

    implicit val timeout: Timeout = Timeout(5.seconds)

    val setupAttackFuture = for {
      attackerCreated <- (blockchainActor ? CreateAccount(attacker)).mapTo[Boolean]
      victim1Created <- (blockchainActor ? CreateAccount(victim1)).mapTo[Boolean]
      victim2Created <- (blockchainActor ? CreateAccount(victim2)).mapTo[Boolean]
      fundAttacker <- if (attackerCreated) {
        (blockchainActor ? UpdateBalance(attacker, fundingAmount)).mapTo[Boolean]
      } else Future.successful(false)
    } yield (attackerCreated, victim1Created, victim2Created, fundAttacker)

    setupAttackFuture.onComplete {
      case Success((attackerCreated, victim1Created, victim2Created, funded)) =>
        if (attackerCreated && victim1Created && victim2Created && funded) {
          Platform.runLater {
            // Create two conflicting transactions
            val transaction1 = Transaction(attacker, victim1, attackAmount)
            val transaction2 = Transaction(attacker, victim2, attackAmount)

            validationLogsBuffer += s"Double-spend attack initiated: Attempting to spend $$${attackAmount} twice"

            // Send first transaction
            validatorActors(currentValidatorIndex) ! ValidateTransaction(transaction1)
            currentValidatorIndex = (currentValidatorIndex + 1) % validatorActors.size

            // Small delay before second transaction
            new Thread(() => {
              Thread.sleep(2000)
              Platform.runLater {
                validatorActors(currentValidatorIndex) ! ValidateTransaction(transaction2)
                currentValidatorIndex = (currentValidatorIndex + 1) % validatorActors.size

                statusLabel.text = s"Double-spend attack simulated: $attacker attempting to spend $$${attackAmount} to both $victim1 and $victim2"
              }
            }).start()
          }
        } else {
          Platform.runLater {
            showErrorDialog("Attack Simulation Failed", "Failed to set up required accounts or fund attacker.")
          }
        }
      case Failure(exception) =>
        Platform.runLater {
          showErrorDialog("Attack Simulation Failed", exception.getMessage)
        }
    }
  }

  // Start New Miner Button Action
  startMinerButton.onAction = handle {
    val newMinerId = s"miner${miningActors.size + 1}"
    val newMinerActor = system.actorOf(MiningActor.props(transactionManager, blockchainActor, difficulty, minerReward, newMinerId, miningLog), s"minerActor${miningActors.size + 1}")
    miningActors = miningActors :+ newMinerActor
    statusLabel.text = s"New miner '$newMinerId' started."
    showInfoDialog("New Miner Started", s"Miner '$newMinerId' has been successfully started.")
  }

  // Helper to dynamically update button states
  private def initializeListeners(): Unit = {
    senderComboBox.value.onChange { (_, _, _) => updateButtonStates() }
    recipientComboBox.value.onChange { (_, _, _) => updateButtonStates() }
    amountField.text.onChange { (_, _, _) => updateButtonStates() }
  }

  private def updateButtonStates(): Unit = {
    val senderSelected = Option(senderComboBox.value.value).exists(_.nonEmpty)
    val recipientSelected = Option(recipientComboBox.value.value).exists(_.nonEmpty)
    val amountValid = Try(amountField.text.value.toDouble).getOrElse(0.0) > 0

    addButton.disable = !(senderSelected && recipientSelected && amountValid)
  }

  private def fetchAndDisplayAccounts(): Unit = {
    implicit val timeout: Timeout = Timeout(5.seconds)
    val accountsFuture = (blockchainActor ? GetAccounts()).mapTo[List[Account]]
    accountsFuture.onComplete {
      case Success(accounts) =>
        val accountNames = accounts.map(_.name)
        Platform.runLater {
          senderComboBox.items = ObservableBuffer(accountNames)
          recipientComboBox.items = ObservableBuffer(accountNames)

          if (senderComboBox.disable.value && accountNames.nonEmpty) {
            senderComboBox.disable = false
          }
          if (recipientComboBox.disable.value && accountNames.nonEmpty) {
            recipientComboBox.disable = false
          }

          if (amountField.disable.value && accountNames.nonEmpty) {
            amountField.disable = false
          }

          accountsListView.items = ObservableBuffer(accounts.map(a => s"${a.name}: $$${a.balance}"))
        }
      case Failure(exception) =>
        showErrorDialog("Error Fetching Accounts", exception.getMessage)
    }
  }

  private def fetchAndDisplayMempool(): Unit = {
    implicit val timeout: Timeout = Timeout(5.seconds)
    val mempoolFuture = (transactionManager ? GetTransactions).mapTo[List[Transaction]]
    mempoolFuture.onComplete {
      case Success(mempool) =>
        Platform.runLater {
          mempoolListView.items = ObservableBuffer(mempool.map(t => s"${t.sender} → ${t.recipient}: $$${t.amount}"))
          mineButton.disable = mempool.isEmpty
        }
      case Failure(exception) =>
        showErrorDialog("Error Fetching Mempool", exception.getMessage)
    }
  }

  private def fetchAndDisplayValidationLogs(): Unit = {
    implicit val timeout: Timeout = Timeout(5.seconds)
    val validationLogsFuture = (validationLog ? GetLogs).mapTo[List[String]]
    validationLogsFuture.onComplete {
      case Success(logs) =>
        Platform.runLater {
          validationListView.items = ObservableBuffer(logs)
        }
      case Failure(exception) =>
        showErrorDialog("Error Fetching Validation Logs", exception.getMessage)
    }
  }

  private def showErrorDialog(dialogTitle: String, message: String): Unit = {
    new Alert(Alert.AlertType.Error) {
      initOwner(null)
      title = dialogTitle
      headerText = None
      contentText = message
    }.showAndWait()
  }

  private def showInfoDialog(dialogTitle: String, message: String): Unit = {
    new Alert(Alert.AlertType.Information) {
      initOwner(null)
      title = dialogTitle
      headerText = None
      contentText = message
    }.showAndWait()
  }

  // Shutdown method to terminate ActorSystem gracefully
  def shutdown(): Unit = {
    system.terminate()
    println("ActorSystem terminated from MainController.")
  }
}
