// src/main/scala/blockchainapp/actors/ClusterListener.scala

package blockchainapp.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import blockchainapp.actors.Messages

class ClusterListener(blockchainPublisher: ActorRef) extends Actor {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    // **Remove classOf[CurrentClusterState] from subscription**
    cluster.subscribe(self, classOf[MemberUp], classOf[MemberRemoved], classOf[UnreachableMember])
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive: Receive = {
    case state: CurrentClusterState =>
      println(s"Current cluster state: $state")

    case MemberUp(member) =>
      println(s"Member is Up: ${member.address}")

    case UnreachableMember(member) =>
      println(s"Member detected as unreachable: ${member.address}")

    case MemberRemoved(member, previousStatus) =>
      println(s"Member is Removed: ${member.address} after $previousStatus")

    case _: MemberEvent =>
    // Handle other member events if necessary
  }
}

object ClusterListener {
  def props(blockchainPublisher: ActorRef): Props = Props(new ClusterListener(blockchainPublisher))
}
