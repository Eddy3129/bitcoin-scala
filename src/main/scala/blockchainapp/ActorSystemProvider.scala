// src/main/scala/blockchainapp/ActorSystemProvider.scala

package blockchainapp

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object ActorSystemProvider {
  // Initialize the ActorSystem with the name "BlockchainCluster"
  lazy val system: ActorSystem = ActorSystem("BlockchainCluster", ConfigFactory.load())
}
