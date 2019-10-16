package com.derivhack.webserver

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.OK
import javax.ws.rs.core.Response.Status.BAD_REQUEST

@CrossOrigin(origins= ["*"])
@RestController
@RequestMapping("/")
class Controller2(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }


    private val proxy = rpc.proxy

    @GetMapping(value = ["/api/peers"])
    private fun retrievePeers(): ResponseEntity<Any> {

        val (status,message) = try {
            OK to mapOf("peers" to proxy.networkMapSnapshot()
                    .map {

                        it.legalIdentities.first().name.organisation }
            )

        }catch (e:Exception){
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }
}
