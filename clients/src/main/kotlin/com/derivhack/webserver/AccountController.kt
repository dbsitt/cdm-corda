package com.derivhack.webserver

import com.derivhack.*
import com.derivhack.webserver.models.AffirmationViewModel
import com.derivhack.webserver.models.ExecutionViewModel
import com.derivhack.webserver.models.TransferViewModel
import com.derivhack.webserver.models.WalletViewModel
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED
import javax.ws.rs.core.Response
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.states.WalletState
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
class AccountController(rpc: NodeRPCConnection) {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @PostMapping(value = ["/api/book/blocktrade"])
    private fun bookBlockTrade(@RequestBody blockTradeJson: String): ResponseEntity<Any> {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(ExecutionFlow::class.java, blockTradeJson)
            val result = tx.returnValue.getOrThrow();
            logger.info("!!! ${tx.id}");
            CREATED to "Block Trade booked with id: ${result.id} "
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
        //return "Wallet is loaded with the Money provided !!!! "
    }

    @PostMapping(value = ["/api/allocateTrade"])
    private fun allocateTrade(@RequestBody allocateTradeJson: String): ResponseEntity<Any> {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(AllocationFlow::class.java, allocateTradeJson)
            val result = tx.returnValue.getOrThrow();
            logger.info("!!! ${tx.id}");
            CREATED to "Trade allocated with id: ${result.id}"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
        //return "Wallet is loaded with the Money provided !!!! "
    }

    @PostMapping(value = ["/api/loadWallet"])
    private fun execution(@RequestBody moneyJson: String): ResponseEntity<Any> {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(WalletFlow::class.java, moneyJson)
            val result = tx.returnValue.getOrThrow();
            logger.info("!!! ${tx.id}");
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
        //return "Wallet is loaded with the Money provided !!!! "
    }

    @PostMapping(value = ["/api/collateralInstruction"])
    private fun collateralInstruction(@RequestBody instructionJson: String): ResponseEntity<Any> {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(CollateralFlow::class.java, instructionJson)
            val result = tx.returnValue.getOrThrow();
            logger.info("!!! ${tx.id}");
            CREATED to "Collateral done with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
        //return "Wallet is loaded with the Money provided !!!! "
    }

    @PostMapping(value = ["/api/settlement"])
    private fun settlement(@RequestBody request: executionRequest): ResponseEntity<Any> {

        val (status,message) = try {
            println(">>>>>>>>> settlement request.executionRef: [${request.executionRef}]")
            val tx = proxy.startFlowDynamic(SettlementFlow::class.java, request.executionRef)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/transfer"])
    private fun transfer(@RequestBody request: executionRequest): ResponseEntity<Any> {

        val (status,message) = try {
            println(">>>>>>>>> transfer request.executionRef: [${request.executionRef}]")
            val tx = proxy.startFlowDynamic(TransferFlow::class.java, request.executionRef)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/confirmation"])
    private fun confirmation(@RequestBody request: executionRequest): ResponseEntity<Any> {

        val (status,message) = try {
            println(">>>>>>>>> confirmation request.executionRef: [${request.executionRef}]")
            val tx = proxy.startFlowDynamic(ConfirmationFlow::class.java, request.executionRef)
            val result = tx.returnValue.getOrThrow();

            CREATED to "Confirmation with the id: ${result.id}"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/affirmation"])
    private fun affirmation(@RequestBody request: executionRequest): ResponseEntity<Any> {
        val (status,message) = try {
            println(">>>>>>>>> affirmation request.executionRef: [${request.executionRef}]")
            val tx = proxy.startFlowDynamic(AffirmationFlow::class.java, request.executionRef)
            val result = tx.returnValue.getOrThrow();
            println(">>>>>>>>>>>>>>>>>>");
            CREATED to "Affirmed with the id: ${result.id}"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
    }

    data class executionRequest(val executionRef: String) {
    }

    @GetMapping(value = ["/api/getAccounts"])
    private fun getAccounts(): List<WalletViewModel> {

        logger.info("!!!!! Inside the getAccounts to get the account details...")
        val walletStates = proxy.vaultQueryBy<WalletState>().states
        logger.info("!!!! walletStates Size" + walletStates.size)
        val walletStateData = walletStates.map { it.state.data }

        val wallets : MutableSet<WalletViewModel> = mutableSetOf()

        walletStateData.map {
            logger.info("!!!!! getting the details for ${it}...")
            logger.info(it.party().toString())
            wallets.add(WalletViewModel(it.ownerPartyName, it.walletReference, it.party().account.accountNumber.value,it.party().account.accountName.value,
                    it.money().currency.value,  it.money().amount.toLong()))
        }

        return wallets.toList();
    }

    @GetMapping(value = ["/getTestAccounts"])
    private fun getTestAccounts(): List<WalletViewModel> {

        logger.info("!!!!! Inside the getAccounts to get the account details...")
        val walletStates = proxy.vaultQueryBy<WalletState>().states

        val wallets : MutableSet<WalletViewModel> = mutableSetOf()
        wallets.add(WalletViewModel("Part1","1", "123", "#CLient123", "USD", 1234566))
        wallets.add(WalletViewModel("Party2","2", "1234", "#CLient124", "USD", 700000))
        wallets.add(WalletViewModel("Party3","3", "1235", "#CLient125", "USD", 800000))
        return wallets.toList()
    }

}
