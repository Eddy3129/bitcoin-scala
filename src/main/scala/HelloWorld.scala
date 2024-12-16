//import akka.actor.typed.ActorRef
//import akka.actor.typed.ActorSystem
//import akka.actor.typed.Behavior
//import akka.actor.typed.scaladsl.Behaviors
//import Echo.Message
//
//object Repeater {
//  sealed trait Command
//  final case object Start extends Command
//  final case object Stop extends Command
//  def apply(): Behavior[Command] =
//    Behaviors.receive { (context, message) =>
//      message match {
//        case Start =>
//          println("starting")
//        case Stop =>
//          Behaviors.stopped
//      }
//      Behaviors.same
//    }
//}
//object Echo {
//  final case class Message(value: String)
//  def apply(): Behavior[Message] =
//    Behaviors.setup { context =>
//      val actor1: ActorRef[Repeater.Command] = context.spawn(Repeater(), "repeater")
//      Behaviors.receiveMessage { message =>
//        println("receive " + message.value)
//        if (message.value == "start"){
//          actor1 ! Repeater.Start
//          Behaviors.same
//        } else if (message.value == "stop") {
//          Behaviors.stopped
//        } else {
//          Behaviors.unhndled
//        }
//      }
//    }
//}
//import scala.io.StdIn
//object MyApp extends App {
//  val greeterMain: ActorSystem[Echo.Message] = ActorSystem(Echo(), "AkkaQuickStart")
//  var command = StdIn.readLine("command=")
//  while ( command != "end") {
//    greeterMain ! Message(command)
//    command = StdIn.readLine("\ncommand=")
//  }
//}
