package net.corda.cdmsupport.states

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.CDMEvent
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import java.math.BigDecimal

@BelongsToContract(CDMEvent::class)
data class CollateralWalletState(
        val moneyJson: String,
        val partyJson: String,
        val walletReference: String,
        val ownerPartyGlobalKey: String,
        val ownerPartyName: String,
        val ownerPartyId: String,
        val lastTransaction: BigDecimal = BigDecimal.ZERO,
        override val participants: List<Party>,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState {

    fun money(): org.isda.cdm.Money {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Money>(moneyJson, org.isda.cdm.Money::class.java)
    }

    fun party(): org.isda.cdm.Party {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Party>(partyJson, org.isda.cdm.Party::class.java)
    }
}