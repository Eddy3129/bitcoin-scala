// src/main/scala/blockchainapp/MainApp.scala

package blockchainapp

import akka.actor.ActorSystem
import javafx.{scene => jfxs}
import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.Scene
import scalafxml.core.{FXMLLoader, NoDependencyResolver}

object MainApp extends JFXApp {

  // Initialize ActorSystem via ActorSystemProvider
  val system: ActorSystem = ActorSystemProvider.system

  // Configure the primary stage
  stage = new PrimaryStage {
    // Retrieve the port from the configuration
    val port = system.settings.config.getInt("akka.remote.artery.canonical.port")
    title = s"Bitcoin Scala - Node ${port - 2550}"
    width = 1000
    height = 700
  }

  // Load the main application UI
  def showMainApp(): Unit = {
    // Use absolute path by starting with '/'
    val resource = getClass.getResource("/blockchainapp/views/MainView.fxml")
    if (resource == null) {
      throw new IllegalArgumentException("Cannot find MainView.fxml")
    }
    val loader = new FXMLLoader(resource, NoDependencyResolver)
    loader.load()

    // Set the scene with the loaded root node
    val root = loader.getRoot[jfxs.layout.AnchorPane]
    stage.scene = new Scene(root)
  }

  // Show the main application window
  showMainApp()

  // Close the window and terminate ActorSystem when "X" button is pressed
  stage.setOnCloseRequest { _ =>
    println("System is closing...")
    system.terminate()
    sys.exit()
  }
}
