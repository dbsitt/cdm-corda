package net.corda.cdmsupport.events

import com.derivhack.AffirmationFlow
import com.derivhack.AllocationFlow
import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.testflow.TestFlowInitiating
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.PartyRoleEnum
import org.isda.cdm.metafields.ReferenceWithMetaExecution
import org.junit.Test
import java.io.FileOutputStream
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UC3_AFFIRMATION : BaseEventTestGJ() {

    val outputFile = FileOutputStream("UC3_AFFIRMATION_REPORT.CSV", true).bufferedWriter()
    val errorFile = FileOutputStream("UC3_AFFIRMATION.ERROR", true).bufferedWriter()
    @Test
    fun allocation() {
        outputFile.write("AFFIRMATION_REFERENCE;ALLOCATION_TRADE_REFERENCE;STATUS")
        outputFile.newLine()
        // --------- new trade
        createBlockTrade("UC1_Block_Trade_BT1.json")
        createBlockTrade("UC1_Block_Trade_BT2.json")
        createBlockTrade("UC1_Block_Trade_BT3.json")
        createBlockTrade("UC1_Block_Trade_BT4.json")
        createBlockTrade("UC1_Block_Trade_BT5.json")
        createBlockTrade("UC1_Block_Trade_BT6.json")
        createBlockTrade("UC1_Block_Trade_BT7.json")
        createBlockTrade("UC1_Block_Trade_BT8.json")
        createBlockTrade("UC1_Block_Trade_BT9.json")
        createBlockTrade("UC1_Block_Trade_BT10.json")

        //----------------allocation
        createAllocationTrade("UC2_Allocation_Trade_AT1.json")
        createAllocationTrade("UC2_Allocation_Trade_AT2.json")
        createAllocationTrade("UC2_Allocation_Trade_AT3.json")
        createAllocationTrade("UC2_Allocation_Trade_AT4.json")
        createAllocationTrade("UC2_Allocation_Trade_AT5.json")
        createAllocationTrade("UC2_Allocation_Trade_AT6.json")
        createAllocationTrade("UC2_Allocation_Trade_AT7.json")
        createAllocationTrade("UC2_Allocation_Trade_AT8.json")
        createAllocationTrade("UC2_Allocation_Trade_AT9.json")
        createAllocationTrade("UC2_Allocation_Trade_AT10.json")

        //----------------affirmation
        createAffirmation("UC3_Affirmation_AT1.1.json")
        createAffirmation("UC3_Affirmation_AT1.2.json")
        createAffirmation("UC3_Affirmation_AT2.1.json")
        createAffirmation("UC3_Affirmation_AT2.2.json")
        createAffirmation("UC3_Affirmation_AT4.1.json")
        createAffirmation("UC3_Affirmation_AT4.2.json")
        createAffirmation("UC3_Affirmation_AT6.1.json")
        createAffirmation("UC3_Affirmation_AT6.2.json")
        createAffirmation("UC3_Affirmation_AT7.1.json")
        createAffirmation("UC3_Affirmation_AT7.2.json")
        createAffirmation("UC3_Affirmation_AT8.1.json")
        createAffirmation("UC3_Affirmation_AT8.2.json")
        createAffirmation("UC3_Affirmation_AT9.1.json")
        createAffirmation("UC3_Affirmation_AT9.2.json")
        createAffirmation("UC3_Affirmation_AT10.1.json")
        createAffirmation("UC3_Affirmation_AT10.2.json")
        outputFile.close()
        errorFile.close()
    }
    fun createBlockTrade(fileName: String){
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${fileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
    }
    fun createAllocationTrade(fileName:String) : Boolean {
        val allocationJson = readTextFromFile("/${samplesDirectory}/${fileName}")
        val allocationEvent = parseEventFromJson(allocationJson)
        val exeBrokerKey = allocationEvent.primitive.allocation.first().after.allocatedTrade.first().execution.partyRole.first() { it.role.equals(PartyRoleEnum.EXECUTING_ENTITY)}.partyReference.globalReference
        val party = allocationEvent.party.first(){ it.meta.globalKey.equals(exeBrokerKey)}.name.value
        val node: TestStartedNode
        if (party == "Broker1") {
            node = node2
        }else if (party == "Broker2") {
            node = node3
        }else{
            println("INVALID ALLOCATION MESSAGE:"+fileName.padEnd(30)+"ERROR DETAIL: Party "+party+" not valid")
            return false
        }
        val future = node.services.startFlow(AllocationFlow(allocationJson)).resultFuture
        try {
            future.getOrThrow()
        }catch(e :Exception) {
            println(e.message)
            return false
        }
        val tx = future.getOrThrow().toLedgerTransaction(node.services)
        return true
    }

    fun createAffirmation(fileName: String) : Boolean {
        val jsonText3 = readTextFromFile("/${samplesDirectory}/${fileName}")
        val affEvt = parseEventFromJson(jsonText3)
        val affExec = affEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution

        //val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val party = affEvt.party.first().name.value
        val node: TestStartedNode
        if (party == "Client1") {
            node = node1
        }else if (party == "Client2") {
            node = node6
        }else if (party == "Client3"){
            node = node7
        }else{
            errorFile.write("INVALID AFFIRMATION MESSAGE:"+fileName.padEnd(30)+"ERROR DETAIL: Party "+party+" not valid")
            errorFile.newLine()
            errorFile.flush()
            return false
        }
        val future3 = node.services.startFlow(AffirmationFlow(affExec.globalReference)).resultFuture
        try {
            future3.getOrThrow()
        }catch(e :Exception) {
            errorFile.write("INVALID AFFIRMATION MESSAGE:"+fileName.padEnd(30)+"ERROR DETAIL:"+e.message)
            errorFile.newLine()
            errorFile.flush()
            return false
        }

        val tx = future3.getOrThrow().toLedgerTransaction(node.services)
        val affirmationState = tx.outputStates.find { it is AffirmationState } as AffirmationState
        outputFile.write(affirmationState.affirmation().identifier.first().meta.globalKey+";"
                                +affirmationState.affirmation().lineage.executionReference.first().globalReference+";"
                                +affirmationState.affirmation().status
        )
        outputFile.newLine()
        outputFile.flush()
        return true
    }
}