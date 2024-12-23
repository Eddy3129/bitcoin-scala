// src/main/scala/blockchainapp/actors/BlockchainPublisher.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import blockchainapp.actors.Messages.{BlockchainUpdated, Subscribe, Unsubscribe}

class BlockchainPublisher extends Actor {

  private var subscribers: Set[ActorRef] = Set()

  def receive: Receive = {
    case Subscribe(subscriber) =>
      subscribers += subscriber
      println(s"Subscriber added: $subscriber")
    case Unsubscribe(subscriber) =>
      subscribers -= subscriber
      println(s"Subscriber removed: $subscriber")
    case BlockchainUpdated(chain) =>
      subscribers.foreach(_ ! BlockchainUpdated(chain))
      println(s"Notified ${subscribers.size} subscribers about blockchain update.")
    case _ =>
      println("BlockchainPublisher received unknown message.")
  }
}

object BlockchainPublisher {
  def props: Props = Props(new BlockchainPublisher)
}
