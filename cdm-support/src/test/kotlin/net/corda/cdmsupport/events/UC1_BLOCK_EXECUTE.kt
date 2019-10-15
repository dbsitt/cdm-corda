package net.corda.cdmsupport.events

import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.PartyRoleEnum
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class UC1_BLOCK_EXECUTE : BaseEventTestGJ() {

    val outputFile = FileOutputStream("UC1_BLOCK_EXECUTE_REPORT.CSV", true).bufferedWriter()
    @Test
    fun execution() {
        outputFile.write("REFERENCE;TRADE_DATE;SETTLEMENT_DATE;CLIENT(ROLE);EXE_BROKER(ROLE);CTPY_BROKER(ROLE);BOND;QUANTITY;CURRENCY;AMOUNT;GROSS_PRICE;NET_PRICE;INTEREST")
        outputFile.newLine()
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
        outputFile.close()
    }

    fun createBlockTrade(fileName:String) {
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${fileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val executionOutputState = tx1.outputStates.find { it is ExecutionState } as ExecutionState
        val evt = parseEventFromJson(jsonText1)
        val clientKey = executionOutputState.execution().partyRole.first() { it.role.equals(PartyRoleEnum.CLIENT) }.partyReference.globalReference
        val executionBrokerKey = executionOutputState.execution().partyRole.first() { it.role.equals(PartyRoleEnum.EXECUTING_ENTITY)}.partyReference.globalReference
        val counterpartyKey = executionOutputState.execution().partyRole.first() { it.role.equals(PartyRoleEnum.COUNTERPARTY)}.partyReference.globalReference
        val clientBuyOrSell = executionOutputState.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(clientKey) }.role
        val executionBrokerBuyOrSell = executionOutputState.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(executionBrokerKey) }.role
        val counterpartyBrokerBuyOrSell = executionOutputState.execution().partyRole.first() { (it.role.equals(PartyRoleEnum.BUYER) || it.role.equals(PartyRoleEnum.SELLER)) && it.partyReference.globalReference.equals(counterpartyKey) }.role
        val security = executionOutputState.execution().product.security.bond.productIdentifier.identifier.first().value
        val quantity = executionOutputState.execution().quantity.amount
        val tradeDate = executionOutputState.execution().tradeDate.value.year.toString() + "-" + executionOutputState.execution().tradeDate.value.month.toString() + "-" + executionOutputState.execution().tradeDate.value.day.toString()
        val settlementDate = executionOutputState.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.year.toString() + "-" + executionOutputState.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.month.toString() + "-" + executionOutputState.execution().settlementTerms.settlementDate.adjustableDate.unadjustedDate.day.toString()
        val currency = executionOutputState.execution().price.grossPrice.currency.value
        val grossPrice = executionOutputState.execution().price.grossPrice.amount
        val accruedInterest = executionOutputState.execution().price.accruedInterest
        val netPrice = executionOutputState.execution().price.netPrice.amount
        val totalAmount = executionOutputState.execution().settlementTerms.settlementAmount.amount
        outputFile.write(evt.eventEffect.effectedExecution.first().globalReference + ";"
                    + tradeDate + ";"
                    + settlementDate + ";"
                    + evt.party.first() { it.meta.globalKey.equals(clientKey) }.name.value + "(" + clientBuyOrSell + ");"
                    + evt.party.first() { it.meta.globalKey.equals(executionBrokerKey) }.name.value + "(" + executionBrokerBuyOrSell + ");"
                    + evt.party.first() { it.meta.globalKey.equals(counterpartyKey) }.name.value + "(" + counterpartyBrokerBuyOrSell + ");"
                    + security + ";"
                    + quantity + ";"
                    + currency + ";"
                    + totalAmount + ";"
                    + grossPrice + ";"
                    + netPrice + ";"
                    + accruedInterest
            )
        outputFile.newLine()
        outputFile.flush()
    }
}
