// src/main/scala/blockchainapp/ActorSystemProvider.scala

package blockchainapp

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

object ActorSystemProvider {
  // Initialize the ActorSystem with the name "BlockchainCluster"
  def createActorSystem(port: Int): ActorSystem = {
    // Define the seed nodes
    val seedNodesConfig = if (port == 2551) {
      // Seed node(s) themselves
      """akka.cluster.seed-nodes = ["akka://BlockchainCluster@127.0.0.1:2551"]"""
    } else {
      // Non-seed nodes join the seed node
      """akka.cluster.seed-nodes = ["akka://BlockchainCluster@127.0.0.1:2551"]"""
    }

    // Load default config and override the port and seed nodes
    val config: Config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port = $port
      akka.remote.artery.bind.port = $port
      akka.remote.artery.canonical.hostname = "127.0.0.1"
      akka.remote.artery.bind.hostname = "0.0.0.0"
      akka.cluster.seed-nodes = [
        "akka://BlockchainCluster@127.0.0.1:2551"
      ]
      """).withFallback(ConfigFactory.load())

    ActorSystem("BlockchainCluster", config)
  }

  // Lazy initialization of the ActorSystem
  lazy val system: ActorSystem = createActorSystem(
    sys.props.get("port").map(_.toInt).getOrElse(2551)
  )
}
