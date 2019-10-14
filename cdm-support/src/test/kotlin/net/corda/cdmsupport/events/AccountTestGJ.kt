package net.corda.cdmsupport.events

import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test

class AccountTestGJ : BaseEventTestGJ() {

    @Test
    fun execution() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC0_acount_AC0_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        future1.getOrThrow().toLedgerTransaction(node2.services)
    }
}
