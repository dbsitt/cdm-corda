package com.derivhack.webserver

import com.derivhack.AffirmationFlow
import com.derivhack.AllocationFlow
import com.derivhack.ConfirmationFlow
import com.derivhack.ExecutionFlow
import com.derivhack.webserver.models.AffirmationViewModel
import com.derivhack.webserver.models.ExecutionViewModel
import com.derivhack.webserver.models.ExecutionViewModel2
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.isda.cdm.Party
import org.isda.cdm.PartyRole
import org.isda.cdm.metafields.ReferenceWithMetaParty
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*
import java.time.format.DateTimeFormatter
import java.util.*
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.Response
import kotlin.collections.HashMap

/**
 * Define your API endpoints here.
 */
@CrossOrigin(origins= ["*"])
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller2(rpc: NodeRPCConnection) {


    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PostMapping(value = ["/api/execution"])
    private fun execution(@RequestBody executionJson: String): Response {

        val (status,message) = try {
            val tx = proxy.startFlowDynamic(ExecutionFlow::class.java, executionJson)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return Response.status(status).entity(message).build();
    }

    @GetMapping(value = ["/api/blocktrades"])
    private fun executionStates(): Response {

        val (status,message) = try {
            val allExecutionStatesAndRefs = proxy.vaultQueryBy<ExecutionState>().states
            val states = allExecutionStatesAndRefs.map { it.state.data }
            CREATED to states.map {
                ExecutionViewModel2(it.linearId.id.toString(), it.participants, it.execution(), it.eventReference, it.workflowStatus,
                        processsExeInfo(it))
            }
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return Response.status(status).entity(message).build();
    }


    /*
    * blocktrade number:
    * "meta": {
                "scheme": null,
                "globalKey": "xxdy/Zsa8dH/GeGisnjJhdqR8cGAuJEU2idvHFlCsuo=",
                "externalKey": "xxdy/Zsa8dH/GeGisnjJhdqR8cGAuJEU2idvHFlCsuo="
            },
    * */

    /*
    * "product": {
                "contractualProduct": null,
                "foreignExchange": null,
                "index": null,
                "loan": null,
                "meta": null,
                "security": {
                    "bond": {
                        "productIdentifier": {
                            "identifier": [
                                {
                                    "value": "DH0371475458",
    * */

    /*
    * "quantity": {
                "amount": "800000.00",
                "currency": null,
                "unit": null
            },
    * */

    /*
    * "netPrice": {
                    "amount": "98.3400",
                    "currency": {
                        "value": "USD",
    * */

    /*
    *
    * cash amt
    *
    * "settlementTerms": {
                "meta": null,
                "settlementCurrency": null,
                "settlementAmount": {
                    "amount": "786720.00",
                    "currency": {
                        "value": "USD",
                        "meta": null
                    },
    * */


    /*
    * value date
    *
    * "settlementDate": {
                    "adjustableDate": {
                        "adjustedDate": null,
                        "dateAdjustments": null,
                        "dateAdjustmentsReference": null,
                        "meta": null,
                        "unadjustedDate": {
                            "day": 17,
                            "month": 10,
                            "year": 2019
                        }
    *
    * */

    /*
    * status:
    *
    * "closedState": null,
    * */


    /*
    * searching for Client Name
    * "partyRole": [
                {
                    "ownershipPartyReference": null,
                    "partyReference": {
                        "globalReference": "xIBwSYskqfYuo71OylXWHFH1u5lbAcAdAnM4LcNheBg=",
                        "externalReference": null,
                        "value": null
                    },
                    "role": "CLIENT"
                },
    *
    *
    * -----
    *
    * "meta": {
                            "scheme": null,
                            "globalKey": "xIBwSYskqfYuo71OylXWHFH1u5lbAcAdAnM4LcNheBg=",
                            "externalKey": "Client1_ID#2_CAOIBGBYU6QPR"
                        },
                        "name": {
                            "value": "Client1",
                            "meta": null
                        },
    * */

    private fun processsExeInfo(state:ExecutionState): Map<String,Any> {

        val info = TreeMap<String,Any>()



        val execution = state.execution();


        /*
        * blocktrade number:
        * "meta": {
                    "scheme": null,
                    "globalKey": "xxdy/Zsa8dH/GeGisnjJhdqR8cGAuJEU2idvHFlCsuo=",
                    "externalKey": "xxdy/Zsa8dH/GeGisnjJhdqR8cGAuJEU2idvHFlCsuo="
                },
        * */

        //blockTrade
        val blockTradeNum = execution.meta.globalKey.orEmpty()
        info["blockTradeNum"]=blockTradeNum

        //status
        val status = execution.closedState?.state?.name.orEmpty()
        info["status"]=status

        /*
        * "quantity": {
                    "amount": "800000.00",
                    "currency": null,
                    "unit": null
                },
        * */

        //quantity
        val quantity = execution.quantity.amount.toPlainString().orEmpty()
        info["quantity"] = quantity

        /*
        * "netPrice": {
                        "amount": "98.3400",
                        "currency": {
                            "value": "USD",
        * */

        //net Price
        val netPrice = execution.price?.netPrice?.amount?.toPlainString().orEmpty()
        info["price"] = netPrice
        //currency
        val currency = execution.price?.netPrice?.currency?.value.orEmpty()
        info["currency"] = currency

        /*
        *
        * cash amt
        *
        * "settlementTerms": {
                    "meta": null,
                    "settlementCurrency": null,
                    "settlementAmount": {
                        "amount": "786720.00",
                        "currency": {
                            "value": "USD",
                            "meta": null
                        },
        * */

        //cashAmt
        val cashAmt = execution.settlementTerms?.settlementAmount?.amount?.toPlainString().orEmpty()
        info["cash"] = cashAmt

        /*
        * value date
        *
        * "settlementDate": {
                        "adjustableDate": {
                            "adjustedDate": null,
                            "dateAdjustments": null,
                            "dateAdjustmentsReference": null,
                            "meta": null,
                            "unadjustedDate": {
                                "day": 17,
                                "month": 10,
                                "year": 2019
                            }
        *
        * */

        //val unadjDate = execution.settlementTerms.settlementDate.adjustableDate.unadjustedDate;

        //value Date
        val valueDate = execution.settlementTerms.settlementDate.adjustableDate.unadjustedDate.toLocalDate()
        info["valueDate"] = valueDate

        /*
        * searching for Client Name
        * "partyRole": [
                    {
                        "ownershipPartyReference": null,
                        "partyReference": {
                            "globalReference": "xIBwSYskqfYuo71OylXWHFH1u5lbAcAdAnM4LcNheBg=",
                            "externalReference": null,
                            "value": null
                        },
                        "role": "CLIENT"
                    },
        *
        *
        * -----
        *
        * "meta": {
                                "scheme": null,
                                "globalKey": "xIBwSYskqfYuo71OylXWHFH1u5lbAcAdAnM4LcNheBg=",
                                "externalKey": "Client1_ID#2_CAOIBGBYU6QPR"
                            },
                            "name": {
                                "value": "Client1",
                                "meta": null
                            },
        * */



        val partyRole = execution.partyRole?.find{ it.role.name == "CLIENT" } as PartyRole

        val partyRoleGlobalRef = partyRole?.partyReference?.globalReference

        val party = execution.party?.find { it.value.meta.globalKey == partyRoleGlobalRef } as ReferenceWithMetaParty

        //client
        val client = party.value.name.value.orEmpty()
        info["client"] = client


        info["productType"] = "Bond"



        return info
    }


}
