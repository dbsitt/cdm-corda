package net.corda.cdmsupport.states

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.CDMEvent
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

@BelongsToContract(CDMEvent::class)
data class WalletState(
        val moneyJson: String,
        val partyJson: String,
        val walletReference: String,
        val ownerPartyGlobalKey: String,
        val ownerPartyName: String,
        val ownerPartyId: String,
        override val participants: List<Party>,
        override val linearId:  UniqueIdentifier = UniqueIdentifier()) : LinearState {

    fun money(): org.isda.cdm.Money {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Money>(moneyJson, org.isda.cdm.Money::class.java)
    }

    fun party(): org.isda.cdm.Party {
        val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        return rosettaObjectMapper.readValue<org.isda.cdm.Party>(partyJson, org.isda.cdm.Party::class.java)
    }
}
