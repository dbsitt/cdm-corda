package net.corda.cdmsupport.events

import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class UC1_BLOCK_EXECUTE_AR : BaseEventTestGJ() {

    val outputFile = FileOutputStream("UC1_BLOCK_EXECUTE_REPORT.CSV", true).bufferedWriter()
    @Test
    fun execution() {
        createBlockTrade("UC1_Block_Trade_BT1.json")
    }

    fun createBlockTrade(fileName:String) {
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${fileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val executionOutputState = tx1.outputStates.find { it is ExecutionState } as ExecutionState
    }



}
