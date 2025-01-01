// src/main/scala/blockchainapp/actors/ClusterListener.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{MemberUp, UnreachableMember}
import Messages.{RequestChain, RespondChain}

class ClusterListener(blockchainPublisher: ActorRef) extends Actor {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp], classOf[UnreachableMember])

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case MemberUp(member) =>
      if (member.address != cluster.selfAddress) {
        println(s"Member is Up: ${member.address}")
        // Request the current chain from the existing member
        val blockchainActor = context.actorSelection(member.address + "/user/blockchainActor")
        blockchainActor ! RequestChain
      }

    case UnreachableMember(member) =>
      println(s"Member detected as unreachable: ${member.address}")

    case _ =>
      println("ClusterListener received unknown message.")
  }
}

object ClusterListener {
  def props(blockchainPublisher: ActorRef): Props = Props(new ClusterListener(blockchainPublisher))
}
