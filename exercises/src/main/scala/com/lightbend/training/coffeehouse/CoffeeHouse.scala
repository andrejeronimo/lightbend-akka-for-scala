package com.lightbend.training.coffeehouse

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

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

  private val finishCoffeeDuration: FiniteDuration = context.system.settings.config.getDuration("coffee-house.guest.finish-coffee-duration", TimeUnit.MILLISECONDS).millis
  private val prepareCoffeeDuration: FiniteDuration = context.system.settings.config.getDuration("coffee-house.barista.prepare-coffee-duration", TimeUnit.MILLISECONDS).millis

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
    context.actorOf(Waiter.props(self), "waiter")
  }

  /** Creates a barista */
  protected def createBarista(): ActorRef = {
    context.actorOf(Barista.props(prepareCoffeeDuration), "barista")
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
