package net.corda.cdmsupport.events

import com.derivhack.AllocationFlow
import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.testflow.TestFlowInitiating
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.PartyRoleEnum
import org.junit.Test
import java.io.FileOutputStream
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UC2_ALLOCATION_TRADE : BaseEventTestGJ() {

    val outputFile = FileOutputStream("UC2_ALLOCATION_TRADE_REPORT.CSV", true).bufferedWriter()
    val errorFile = FileOutputStream("UC2_ALLOCATION_TRADE.ERROR", true).bufferedWriter()
    @Test
    fun allocation() {
        outputFile.write("ALC_REFERENCE;BLK_REFERENCE;TRADE_DATE;SETTLEMENT_DATE;CLIENT(ROLE);EXE_BROKER(ROLE);CTPY_BROKER(ROLE);BOND;ALC_QUANTITY;BLK_QUANTITY;CURRENCY;ALC_AMOUNT;BLK_AMOUNT;GROSS_PRICE;NET_PRICE;INTEREST")
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
            errorFile.write("INVALID ALLOCATION MESSAGE:"+fileName.padEnd(30)+"ERROR DETAIL: Party "+party+" not valid")
            errorFile.newLine()
            errorFile.flush()
            return false
        }
        val future = node.services.startFlow(AllocationFlow(allocationJson)).resultFuture
        try {
            future.getOrThrow()
        }catch(e :Exception) {
            errorFile.write("INVALID ALLOCATION MESSAGE:"+fileName.padEnd(30)+"ERROR DETAIL:"+e.message)
            errorFile.newLine()
            errorFile.flush()
            return false
        }
        val tx = future.getOrThrow().toLedgerTransaction(node.services)
        val blockTrade = tx.outputStates[0] as ExecutionState
        val allocationTrade1 = tx.outputStates[1] as ExecutionState
        val allocationTrade2 = tx.outputStates[2] as ExecutionState
        val allocationTrade1clientKey = allocationTrade1.execution().partyRole.first() { it.role.equals(PartyRoleEnum.CLIENT) }.partyReference.globalReference
        val allocationTrade1executionBrokerKey = allocationTrade1.execution().partyRole.first() { it.role.equals(PartyRoleEnum.EXECUTING_ENTITY)}.partyReference.globalReference
        val allocationTrade1counterpartyKey = allocationTrade1.execution().partyRole.first() { it.role.equals(PartyRoleEnum.COUNTERPARTY)}.partyReference.globalReference
        val allocationTrade1clientBuyOrSell = allocationTrade1.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade1clientKey) }.role
        val allocationTrade1executionBrokerBuyOrSell = allocationTrade1.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade1executionBrokerKey) }.role
        val allocationTrade1counterpartyBrokerBuyOrSell = allocationTrade1.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade1counterpartyKey) }.role
        val allocationTrade1tradeDate = allocationTrade1.execution().tradeDate.value.year.toString() + "-" + allocationTrade1.execution().tradeDate.value.month.toString() + "-" + allocationTrade1.execution().tradeDate.value.day.toString()
        val allocationTrade1settlementDate = allocationTrade1.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.year.toString() + "-" + allocationTrade1.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.month.toString() + "-" + allocationTrade1.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.day.toString()
        val allocationTrade2clientKey = allocationTrade2.execution().partyRole.first() { it.role.equals(PartyRoleEnum.CLIENT) }.partyReference.globalReference
        val allocationTrade2executionBrokerKey = allocationTrade2.execution().partyRole.first() { it.role.equals(PartyRoleEnum.EXECUTING_ENTITY)}.partyReference.globalReference
        val allocationTrade2counterpartyKey = allocationTrade2.execution().partyRole.first() { it.role.equals(PartyRoleEnum.COUNTERPARTY)}.partyReference.globalReference
        val allocationTrade2clientBuyOrSell = allocationTrade2.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade2clientKey) }.role
        val allocationTrade2executionBrokerBuyOrSell = allocationTrade2.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade2executionBrokerKey) }.role
        val allocationTrade2counterpartyBrokerBuyOrSell = allocationTrade2.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(allocationTrade2counterpartyKey) }.role
        val allocationTrade2tradeDate = allocationTrade2.execution().tradeDate.value.year.toString() + "-" + allocationTrade2.execution().tradeDate.value.month.toString() + "-" + allocationTrade2.execution().tradeDate.value.day.toString()
        val allocationTrade2settlementDate = allocationTrade2.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.year.toString() + "-" + allocationTrade2.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.month.toString() + "-" + allocationTrade2.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.day.toString()

        outputFile.write(
                allocationTrade1.execution().meta.globalKey+";"
                        +blockTrade.execution().meta.globalKey+";"
                        +allocationTrade1tradeDate+";"
                        +allocationTrade1settlementDate+";"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade1clientKey) }.name.value + "(" + allocationTrade1clientBuyOrSell + ");"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade1executionBrokerKey) }.name.value + "(" + allocationTrade1executionBrokerBuyOrSell + ");"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade1counterpartyKey) }.name.value + "(" + allocationTrade1counterpartyBrokerBuyOrSell + ");"
                        + allocationTrade1.execution().product.security.bond.productIdentifier.identifier.first().value + ";"
                        + allocationTrade1.execution().quantity.amount + ";"
                        + blockTrade.execution().quantity.amount + ";"
                        + allocationTrade1.execution().price.grossPrice.currency.value + ";"
                        + allocationTrade1.execution().settlementTerms.settlementAmount.amount + ";"
                        + blockTrade.execution().settlementTerms.settlementAmount.amount + ";"
                        + allocationTrade1.execution().price.grossPrice.amount + ";"
                        + allocationTrade1.execution().price.netPrice.amount + ";"
                        + allocationTrade1.execution().price.accruedInterest
        )
        outputFile.newLine()
        outputFile.write(
                allocationTrade2.execution().meta.globalKey+";"
                        +blockTrade.execution().meta.globalKey+";"
                        +allocationTrade2tradeDate+";"
                        +allocationTrade2settlementDate+";"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade2clientKey) }.name.value + "(" + allocationTrade2clientBuyOrSell + ");"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade2executionBrokerKey) }.name.value + "(" + allocationTrade2executionBrokerBuyOrSell + ");"
                        + allocationEvent.party.first() { it.meta.globalKey.equals(allocationTrade2counterpartyKey) }.name.value + "(" + allocationTrade2counterpartyBrokerBuyOrSell + ");"
                        + allocationTrade2.execution().product.security.bond.productIdentifier.identifier.first().value + ";"
                        + allocationTrade2.execution().quantity.amount + ";"
                        + blockTrade.execution().quantity.amount + ";"
                        + allocationTrade2.execution().price.grossPrice.currency.value + ";"
                        + allocationTrade2.execution().settlementTerms.settlementAmount.amount + ";"
                        + blockTrade.execution().settlementTerms.settlementAmount.amount + ";"
                        + allocationTrade2.execution().price.grossPrice.amount + ";"
                        + allocationTrade2.execution().price.netPrice.amount + ";"
                        + allocationTrade2.execution().price.accruedInterest
        )
        outputFile.newLine()
        outputFile.flush()
        return true
    }
}