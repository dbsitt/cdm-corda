package net.corda.cdmsupport.functions

import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.MetaFields
import java.math.BigDecimal

class TransferBuilderFromExecution {
    fun transferBuilder(state: ExecutionState): Event {
        val client = extractParty(state, PartyRoleEnum.CLIENT)
        val broker = extractParty(state, PartyRoleEnum.EXECUTING_ENTITY)
        val settlementAgent = extractParty(state, PartyRoleEnum.SETTLEMENT_AGENT)
        val transferPrimitive = createTransfer(state)
        val primitiveEvent = PrimitiveEvent.builder().addTransfer(transferPrimitive).build()

        //val event = Event.builder().build()
        //val hash = SerialisingHashFunction().hash(event)
        val eventBuilder = Event.builder()
                .addParty(client.value)
                .addParty(broker.value)
                .addParty(settlementAgent.value)
                .addEventIdentifier(generateIdentifier(settlementAgent, generateNameKey()))
                .setPrimitive(primitiveEvent)
        return eventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(eventBuilder.build())).build()).build()
    }

/*    fun createTransfer(state: ExecutionState): TransferPrimitive {
        val transferBuilder = TransferPrimitive.builder()
                .setSettlementType(TransferSettlementEnum.DELIVERY_VERSUS_PAYMENT)
                .setSettlementDate(AdjustableOrAdjustedOrRelativeDate.builder()
                        .build())
                .setStatus(TransferStatusEnum.SETTLED)
                .addCashTransfer(CashTransferComponent.builder()
                        .setAmount(Money.builder().build())
                        .setPayerReceiver(PayerReceiver.builder()
                                .setPayerPartyReference(extractParty(state, PartyRoleEnum.CLIENT))
                                .setPayerPartyReference(extractParty(state, PartyRoleEnum.EXECUTING_ENTITY))
                                .build())
                        .build())
                .addSecurityTransfer(SecurityTransferComponent.builder()
                        .setQuantity(state.execution().quantity.amount)
                        .setSecurity(state.execution().product.security)
                        .setAssetTransferType(AssetTransferTypeEnum.FREE_OF_PAYMENT) // ???
                        .setTransferorTransferee(
                                TransferorTransferee.TransferorTransfereeBuilder()
                                        .setTransfereePartyReference(extractParty(state, PartyRoleEnum.CLIENT))
                                        .setTransferorPartyReference(extractParty(state, PartyRoleEnum.EXECUTING_ENTITY))
                                        .build())
                        .build())
                .setSettlementReference(state.execution().settlementTerms.meta.globalKey)
        return transferBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(transferBuilder.build())).build()).build()
    }*/

    fun createTransfer(state: ExecutionState): TransferPrimitive {
        val execution = state.execution()
        val transferBuilder = TransferPrimitive.builder()
                .setSettlementType(TransferSettlementEnum.DELIVERY_VERSUS_PAYMENT)
                .setSettlementDate(AdjustableOrAdjustedOrRelativeDate.builder().setUnadjustedDate(execution.settlementTerms.settlementDate.adjustableDate.unadjustedDate).build())
                .setStatus(TransferStatusEnum.SETTLED)
                .addCashTransfer(CashTransferComponent.builder()
                        .setAmount(Money.builder().setAmount(execution.settlementTerms.settlementAmount.amount)
                                .setCurrency(execution.price.netPrice.currency)
                                .build())
                        .setPayerReceiver(PayerReceiver.builder()
                                .setPayerPartyReference(extractParty(state, PartyRoleEnum.CLIENT))
                                .setReceiverPartyReference(extractParty(state, PartyRoleEnum.EXECUTING_ENTITY))
                                .build())
                        .build())
                .addSecurityTransfer(SecurityTransferComponent.builder()
                        .setQuantity(execution.quantity.amount)
                        .setSecurity(execution.product.security)
                        .setAssetTransferType(AssetTransferTypeEnum.FREE_OF_PAYMENT) // ???
                        .setTransferorTransferee(TransferorTransferee.TransferorTransfereeBuilder()
                                .setTransfereePartyReference(extractParty(state, PartyRoleEnum.CLIENT))
                                .setTransferorPartyReference(extractParty(state, PartyRoleEnum.EXECUTING_ENTITY))
                                .build())
                        .build())
                .setSettlementReference(state.execution().settlementTerms.meta.globalKey)
        return transferBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(transferBuilder.build())).build()).build()
    }

    fun addMoneyAmount(inputMoney: Money, addAmount: BigDecimal): Money {
        return inputMoney.toBuilder().setAmount(inputMoney.amount.add(addAmount)).build()
    }

    fun minusMoneyAmount(inputMoney: Money, addAmount: BigDecimal): Money {
        return inputMoney.toBuilder().setAmount(inputMoney.amount.minus(addAmount)).build()
    }
}