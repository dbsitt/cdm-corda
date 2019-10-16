package net.corda.cdmsupport.functions

import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import java.time.ZoneId
import java.time.ZonedDateTime



fun processTransferPrimitive(transferPrimitive: TransferPrimitive){


    val positionStateBuilder = Position.builder()

    transferPrimitive.securityTransfer?.forEach{
        

    }

}

fun processSecurityTransfer(secComp:SecurityTransferComponent){
    secComp.quantity
    secComp.security
    secComp.transferorTransferee.transfereeAccountReference

}

fun processEvt(evt:Event){

   // evt.primitive.transfer.forEach { it. }


}

fun buildPortfolio(state:ExecutionState): Portfolio {





    //val state : ExecutionState;
    val refInfo = state.execution();

    val partyRole = refInfo.partyRole.first { it.role == PartyRoleEnum.SETTLEMENT_AGENT }
    val partyRef = partyRole.partyReference.globalReference
    val party = state.execution().party.first { it.globalReference == partyRef }



    val aggBuilder = AggregationParameters.builder();
    val dateTime = ZonedDateTime.of(2019,10,17,
            0, 0, 0, 0, ZoneId.of("UTC"))

    aggBuilder.setDateTime(dateTime).addProduct(refInfo.product).addParty(party)

    val psBuilder = PortfolioState.builder()
    psBuilder.setLineage(Lineage.LineageBuilder()
            .addEventReference(ReferenceWithMetaEvent.ReferenceWithMetaEventBuilder()
                    .setGlobalReference(state.eventReference).build())
            .addExecutionReference(ReferenceWithMetaExecution.ReferenceWithMetaExecutionBuilder()
                    .setGlobalReference(state.execution().meta.globalKey).build()).build())

    psBuilder.setMeta(MetaFields.builder().setGlobalKey("YlKpJEBIVtSTVbIh9/NWs5nsE5VdnXSml/+T8ZdgzQE=").build())

    psBuilder.addPositions(Position.builder().build())

    val builder = Portfolio.PortfolioBuilder();
    builder.setAggregationParameters(aggBuilder.build()).setPortfolioState(psBuilder.build())





    return builder.build()
}