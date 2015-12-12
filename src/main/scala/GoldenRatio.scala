import akka.actor._
import akka.routing.RoundRobinPool
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import math.pow

object GoldenRatio extends App {

  sealed trait GRMessage
  case object Calculate extends GRMessage
  case class Work(start: Int, nrOfElements: Int) extends GRMessage
  case class Result(value: Double) extends GRMessage
  case class GRApproximation(gr: Double, duration: Duration)

  class Worker extends Actor {
    def calculateGRFor(start: Int, nrOfElements: Int): Double = {

      def factorial(n:Int): Double = n match {
        case 0 => 1.0
        case _ => n * factorial(n-1)
      }

      (start until (start+nrOfElements)).map( x => pow(-1, x+1) * factorial(2*x+1) / factorial(x+2) / factorial(x) / pow(4, 2*x+3)).sum
    }

    def receive = {
      case Work(start, nrOfElements) => sender ! Result(calculateGRFor(start, nrOfElements))
    }
  }

  class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef) extends Actor {
    var gr: Double = 13.0 / 8.0
    var nrOfResults: Int = _
    val start: Long = System.currentTimeMillis
    val workerRouter = context.actorOf(Props[Worker].withRouter(RoundRobinPool(nrOfWorkers)), name = "workerRouter")

    def receive = {
      case Calculate => for (i ← 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements)
      case Result(value) =>
        gr += value
        nrOfResults += 1
        if (nrOfResults == nrOfMessages) {
          listener ! GRApproximation(gr, duration = (System.currentTimeMillis - start).millis)
          context.stop(self)
        }
    }
  }

  class Listener extends Actor {
    def receive = {
      case GRApproximation(gr, duration) =>
        println("\n\tGR approximation: \t\t%s\n\tCalculation time: \t%s".format(gr, duration))
        context.system.terminate()
    }
  }

  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {
    val system = ActorSystem("GRSystem")
    val listener = system.actorOf(Props[Listener], name = "listener")
    val master = system.actorOf(Props(new Master(nrOfWorkers, nrOfMessages, nrOfElements, listener)), name = "master")

    master ! Calculate
  }

  // Change parameters here!
  calculate(nrOfWorkers = 10, nrOfElements = 5, nrOfMessages = 10)
}