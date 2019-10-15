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

    @PostMapping(value = ["/loadWallet"])
    private fun execution(@RequestBody moneyJson: String): Response  {
        val (status,message) = try {
            val tx = proxy.startFlowDynamic(WalletFlow::class.java, moneyJson)
            val result = tx.returnValue.getOrThrow();
            logger.info("!!! ${tx.id}");
            CREATED to "Transaction with id: ${result.id} created"
        }catch(e :Exception) {
            BAD_REQUEST to e.message
        }
        return Response.status(status).entity(message).build();
            //return "Wallet is loaded with the Money provided !!!! "
    }

    @GetMapping(value = ["/getAccounts"])
    private fun getAccounts(): List<WalletViewModel> {

        logger.info("!!!!! Inside the getAccounts to get the account details...")
        val walletStates = proxy.vaultQueryBy<WalletState>().states
        val walletStateData = walletStates.map { it.state.data }

        return walletStateData.map {
            logger.info("!!!!! getting the details for ${it}...")
            logger.info(it.party().toString())
            WalletViewModel(it.walletReference, it.party().account.accountNumber.toString(),it.party().account.accountName.toString(),
                    it.money().currency.toString(),  it.money().amount.longValueExact())
        }
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
