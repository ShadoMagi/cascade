package com.paypal.stingray.http.tests.resource

import com.paypal.stingray.common.values.StaticValuesFromServiceNameComponent
import com.paypal.stingray.common.service.ServiceNameComponent
import spray.routing.Directives._
import com.paypal.stingray.http.resource.{ResourceServiceComponent, ResourceDriver}
import com.paypal.stingray.http.actor.ActorSystemComponent

/**
 * A dummy resource service implementation for use with [[com.paypal.stingray.http.tests.resource.DummyResource]].
 * Only accepts requests to the "/ping" endpoint.
 */
trait DummyResourceService
  extends ServiceNameComponent
  with ResourceServiceComponent
  with ActorSystemComponent
  with StaticValuesFromServiceNameComponent {

  /** This resource */
  val dummy = new DummyResource

  /** The route for this resource */
  override val route = {
    path("ping") {
      get {
        ResourceDriver.serve(dummy, Map())
      }
    }
  }

  override lazy val serviceName = "tests"

}
