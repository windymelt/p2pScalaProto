package momijikawa.p2pscalaproto

import scala.concurrent.duration.FiniteDuration
import scalaz._
import Scalaz._

class UPnPOpener(val external_port: Int, val local_port: Int, val protocol: String, val description: String, val limit: FiniteDuration)(val log: akka.event.LoggingAdapter) {

  import com.psyonik.upnp._

  def open = {
    GatewayDiscover().getValidGateway() >>= {
      gw =>
        log.debug("UPnP: found Gateway")
        val lcl_addr = gw.localAddress
        val ext_addr = gw.externalIPAddress

        gw.getSpecificPortMappingEntry(external_port, protocol) match {
          case Some(_) =>
            log.warning("UPnP: port mapping already exists")
            None
          case None =>
            log.debug("requesting port mapping...")
            gw.addPortMapping(external_port, local_port, lcl_addr.getHostAddress, protocol, description, limit.toSeconds.toInt).some
        }
    }
  } | false

  def close = {
    GatewayDiscover().getValidGateway() >>= {
      gw =>
        gw.getSpecificPortMappingEntry(external_port, protocol) match {
          case Some(_) =>
            gw.deletePortMapping(external_port, protocol).some
          case None => None
        }
    }
  } | false

  def getExternalAddress = {
    GatewayDiscover().getValidGateway() >>= {
      gw =>
        gw.externalIPAddress
    }
  }

}
