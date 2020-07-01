package com.lightbend.training.coffeehouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, SupervisorStrategy, Terminated}

import scala.concurrent.duration._

object CoffeeHouse {
  case class CreateGuest(favoriteCoffee: Coffee, caffeineLimit: Int)
  case class ApproveCoffee(coffee: Coffee, guest: ActorRef)

  def props(caffeineLimit: Int): Props = {
    Props(new CoffeeHouse(caffeineLimit))
  }
}

class CoffeeHouse(caffeineLimit: Int) extends Actor with ActorLogging {
  import CoffeeHouse._

  // Log a message when the CoffeeHouse is instantiated
  log.debug("CoffeeHouse Open")

  /** Defines the supervisor strategy
   *  If a Guest throws CaffeineException then the Guest will be stopped
   *  Otherwise the default supervisor strategy decider is used */
  override def supervisorStrategy: SupervisorStrategy = {
    val decider: SupervisorStrategy.Decider = {
      case Guest.CaffeineException => SupervisorStrategy.Stop
      case Waiter.FrustratedException(coffee, guest) =>
        // Guarantee that the coffee that generated the frustration is not loss.
        // We need to use a forward in order to have the Barista responding to the Waiter. If a tell was used the
        // Barista would respond to the the CoffeeHouse.
        barista.forward(Barista.PrepareCoffee(coffee, guest))
        SupervisorStrategy.Restart
    }

    OneForOneStrategy()(decider.orElse(super.supervisorStrategy.decider))
  }

  private val config = context.system.settings.config
  private val finishCoffeeDuration: FiniteDuration = config.getDuration("coffee-house.guest.finish-coffee-duration", TimeUnit.MILLISECONDS).millis
  private val prepareCoffeeDuration: FiniteDuration = config.getDuration("coffee-house.barista.prepare-coffee-duration", TimeUnit.MILLISECONDS).millis
  private val baristaAccuracy: Int = config.getInt("coffee-house.barista.accuracy")
  private val waiterMaxComplaintCount: Int = config.getInt("coffee-house.waiter.max-complaint-count")

  private var guestBook: Map[ActorRef, Int] = Map.empty.withDefaultValue(0)

  // CoffeeHouse barista and waiter
  private val barista: ActorRef = createBarista()
  private val waiter: ActorRef = createWaiter()

  /** Creates a guest */
  protected def createGuest(favoriteCoffee: Coffee, guestCaffeineLimit: Int): ActorRef = {
    val guest = context.actorOf(Guest.props(waiter, favoriteCoffee, finishCoffeeDuration, guestCaffeineLimit))
    context.watch(guest)  // Monitor each guest by using dead watch
    guest
  }

  /** Creates a waiter */
  protected def createWaiter(): ActorRef = {
    context.actorOf(Waiter.props(self, barista, waiterMaxComplaintCount), "waiter")
  }

  /** Creates a barista */
  protected def createBarista(): ActorRef = {
    context.actorOf(Barista.props(prepareCoffeeDuration, baristaAccuracy), "barista")
  }

  override def receive: Receive = {
    case CreateGuest(favoriteCoffee, guestCaffeineLimit) =>
      val guest = createGuest(favoriteCoffee, guestCaffeineLimit)
      guestBook += (guest -> 0)
      log.info(s"Guest $guest added to guest book")
    case ApproveCoffee(coffee, guest) if guestBook(guest) < caffeineLimit =>
      log.info(s"Guest $guest caffeine count incremented.")
      guestBook += (guest -> (guestBook(guest) + 1))
      barista.forward(Barista.PrepareCoffee(coffee, guest))
    case ApproveCoffee(_, guest) =>
      log.info(s"Sorry, $guest, but you have reached your limit.")
      context.stop(guest)
    case Terminated(guest) =>
      guestBook -= guest
      log.info(s"Thanks $guest, for being our guest!")
  }

}
