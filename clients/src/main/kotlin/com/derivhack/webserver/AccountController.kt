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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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

    @PostMapping(value = ["/api/settlement"])
    private fun settlement(@RequestParam reference: String): ResponseEntity<Any> {

        val (status,message) = try {
            val tx = proxy.startFlowDynamic(SettlementFlow::class.java, reference)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/transfer"])
    private fun transfer(@RequestParam reference: String): ResponseEntity<Any> {

        val (status,message) = try {
            val tx = proxy.startFlowDynamic(TransferFlow::class.java, reference)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }

        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/confirmation"])
    private fun confirmation(@RequestParam executionRef: String): ResponseEntity<Any> {

        val (status,message) = try {
            val tx = proxy.startFlowDynamic(ConfirmationFlow::class.java, executionRef)
            val result = tx.returnValue.getOrThrow();

            CREATED to "Confirmation with the id: ${result.id}"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
    }

    @PostMapping(value = ["/api/affirmation"])
    private fun affirmation(@RequestParam executionRef: String): ResponseEntity<Any> {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(AffirmationFlow::class.java, executionRef)
            val result = tx.returnValue.getOrThrow();
            CREATED to "Affirmed with the id: ${result.id}"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return ResponseEntity.status(status.statusCode).body(message)
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
            wallets.add(WalletViewModel(it.walletReference, it.party().account.accountNumber.value,it.party().account.accountName.value,
                    it.money().currency.value,  it.money().amount.toLong()))
        }

        return wallets.toList();
    }

    @GetMapping(value = ["/getTestAccounts"])
    private fun getTestAccounts(): List<WalletViewModel> {

        logger.info("!!!!! Inside the getAccounts to get the account details...")
        val walletStates = proxy.vaultQueryBy<WalletState>().states

        val wallets : MutableSet<WalletViewModel> = mutableSetOf()
        wallets.add(WalletViewModel("1", "123", "#CLient123", "USD", 1234566))
        wallets.add(WalletViewModel("2", "1234", "#CLient124", "USD", 700000))
        wallets.add(WalletViewModel("3", "1235", "#CLient125", "USD", 800000))
        return wallets.toList()
    }

}
