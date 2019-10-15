package com.derivhack.webserver

import com.derivhack.AffirmationFlow
import com.derivhack.AllocationFlow
import com.derivhack.ConfirmationFlow
import com.derivhack.ExecutionFlow
import com.derivhack.webserver.models.AffirmationViewModel
import com.derivhack.webserver.models.ExecutionViewModel
import com.derivhack.webserver.models.TransferViewModel
import com.derivhack.webserver.models.WalletViewModel
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.states.WalletState
import net.corda.core.messaging.vaultQueryBy
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

    @GetMapping(value = ["/getAccounts"])
    private fun getAccounts(): List<WalletViewModel> {

        logger.info("!!!!! Inside the getAccounts to get the account details...")
        val walletStates = proxy.vaultQueryBy<WalletState>().states
        val walletStateData = walletStates.map { it.state.data }

        return walletStateData.map {
            logger.info("!!!!! getting the details for ${it}...")
            logger.info(it.party().toString())
            WalletViewModel(it.walletReference, it.party().account.accountNumber.toString(),it.party().account.accountName.toString())
        }
    }

}
