package com.derivhack.webserver

import com.derivhack.AllocationFlow
import com.derivhack.AllocationFlowJson
import com.derivhack.webserver.models.ExecutionViewModel2
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.isda.cdm.PartyRole
import org.isda.cdm.metafields.ReferenceWithMetaParty
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.util.*
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.Response.Status.OK

import org.springframework.http.ResponseEntity

@CrossOrigin(origins= ["*"])
@RestController
class AllocationControllerExt (rpc: NodeRPCConnection)  {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PostMapping(value = ["/api/allocation"])
    @Throws(java.lang.RuntimeException::class)
    private fun allocation(@RequestBody allocationJson: String): ResponseEntity<Any> {

        val (status,message) = try {
            val tx = proxy.startFlowDynamic(AllocationFlowJson::class.java, allocationJson)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch (e:Exception){
            BAD_REQUEST to e.message

        }

        return ResponseEntity.status(status.statusCode).body(message)

    }

    @GetMapping(value = ["/api/allocations"])
    private fun executionStates(): ResponseEntity<Any> {

        val (status,message) = try {

            val allExecutionStatesAndRefs = proxy.vaultQueryBy<ExecutionState>().states
            /*for(exe in allExecutionStatesAndRefs) {
                println(exe.state.data.workflowStatus)
            }*/
            val states = allExecutionStatesAndRefs.
                    filter { it.state.data.execution().meta.globalKey != it.state.data.execution().meta.externalKey }.
                    map { it.state.data }
            OK to states.map {
                ExecutionViewModel2(it.linearId.id.toString(), it.participants, it.execution(), it.eventReference, it.workflowStatus,
                        processsAlocInfo(it))
            }
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }


    private fun processsAlocInfo(state:ExecutionState): Map<String,Any> {

        val info = TreeMap<String,Any>()
        try{

            val execution = state.execution();


            //blockTrade
            val blockTradeNum = execution.meta.globalKey.orEmpty()
            info["blockTradeNum"] = blockTradeNum

            //status
            val status = execution.closedState?.state?.name.orEmpty()
            info["status"] = status


            //quantity
            val quantity = execution.quantity.amount.toPlainString().orEmpty()
            info["quantity"] = quantity


            //net Price
            val netPrice = execution.price?.netPrice?.amount?.toPlainString().orEmpty()
            info["price"] = netPrice
            //currency
            val currency = execution.price?.netPrice?.currency?.value.orEmpty()
            info["currency"] = currency


            //cashAmt
            val cashAmt = execution.settlementTerms?.settlementAmount?.amount?.toPlainString().orEmpty()
            info["cash"] = cashAmt


            //value Date
            val valueDate = execution.settlementTerms.settlementDate.adjustableDate.unadjustedDate.toLocalDate()
            info["valueDate"] = valueDate


            val partyRole = execution.partyRole?.find { it.role.name == "CLIENT" } as PartyRole

            val partyRoleGlobalRef = partyRole.partyReference?.globalReference

            val party = execution.party?.find { it.value.meta.globalKey == partyRoleGlobalRef } as ReferenceWithMetaParty

            //client
            val client = party.value.name.value.orEmpty()
            info["client"] = client


            info["productType"] = "Bond"


            val productLabel = execution.product?.security?.bond?.productIdentifier?.identifier?.single()?.value.orEmpty()

            info["product"] = productLabel

        }catch (e:Exception){
            logger.debug("Error - ", e)
        }


        return info;
    }
}