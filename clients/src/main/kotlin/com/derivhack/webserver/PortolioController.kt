package com.derivhack.webserver

import com.derivhack.*
import com.derivhack.webserver.models.*
import net.corda.cdmsupport.states.*
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.Response
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.json.Json
import javax.json.JsonObject

/**
 * Define your API endpoints here.
 */
@CrossOrigin(origins= ["*"])
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class PortolioController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy
    @GetMapping(value = ["/api/portfolio"])
    private fun portfolio(): List<PortfolioViewModel> {
        val allExecutionStatesAndRefs = proxy.vaultQueryBy<DBSPortfolioState>().states
        val states = allExecutionStatesAndRefs.map { it.state.data }

        return states.map {
            PortfolioViewModel(it.executionRef,it.participants,it.portfolio())
        }
    }
}
