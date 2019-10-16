package net.corda.cdmsupport.functions

import org.isda.cdm.*
import org.isda.cdm.metafields.ReferenceWithMetaExecution

fun createPortfoliosFromTransfer(transferPrimitive: TransferPrimitive, executionRef: String): List<Portfolio> {
    return listOf(createPortfolioFromTransfer(transferPrimitive, false, executionRef), createPortfolioFromTransfer(transferPrimitive, true, executionRef))
}

private fun createPortfolioFromTransfer(transferPrimitive: TransferPrimitive, isPayer: Boolean, executionRef: String): Portfolio {
    val moneyAmount = if (isPayer) transferPrimitive.cashTransfer.first().amount.amount.negate() else transferPrimitive.cashTransfer.first().amount.amount
    val securityAmount = if (isPayer) transferPrimitive.securityTransfer.first().quantity else transferPrimitive.securityTransfer.first().quantity.negate()
    val partyRef = if(isPayer) transferPrimitive.cashTransfer.first().payerReceiver.payerPartyReference else transferPrimitive.cashTransfer.first().payerReceiver.receiverPartyReference
    val portfolioBuilder = Portfolio.builder()
            .setAggregationParameters(AggregationParameters.builder()
                    .addParty(partyRef)
                    .addProduct(Product.builder()
                            .setSecurity(transferPrimitive.securityTransfer.first().security)
                            .build())
                    .build())
            .setPortfolioState(PortfolioState.builder()
                    .setLineage(Lineage.builder()
//                            .addTransferReference(ReferenceWithMetaTransferPrimitive.builder()
//                                    .setGlobalReference(transferPrimitive.meta.globalKey)
//                                    .build())
                            .addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(executionRef).build())
                            .build())
                    .addPositions(Position.builder()
                            .setCashBalance(
                                    Money.builder()
                                            .setCurrency(transferPrimitive.cashTransfer.first().amount.currency)
                                            .setAmount(moneyAmount)
                                            .build()
                            )
                            .setQuantity(Quantity.builder()
                                    .setAmount(securityAmount)
                                    .build())
                            .setProduct(
                                    Product.builder()
                                            .setSecurity(transferPrimitive.securityTransfer.first().security)
                                            .build())
                            .setPositionStatus(PositionStatusEnum.EXECUTED)
                            .build())
                    .build())

    return portfolioBuilder.build()
}