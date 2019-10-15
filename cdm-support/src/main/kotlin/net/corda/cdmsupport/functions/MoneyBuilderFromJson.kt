package net.corda.cdmsupport.functions

import com.rosetta.model.lib.RosettaModelObject
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.parseMoneyFromJson
import net.corda.cdmsupport.eventparsing.parsePartyFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.FieldWithMetaString
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.metafields.ReferenceWithMetaParty
import org.isda.cdm.rosettakey.SerialisingHashFunction
import java.math.BigDecimal

class MoneyBuilderFromJson {
    fun moneyBuilder(moneyJson: String): Money {
        val inputMoney = parseMoneyFromJson(moneyJson)
        println("inputMoney ##############################")
        println(inputMoney)
        println("inputMoney ##############################")

        val newMoney = inputMoney.toBuilder()
                .setMeta(MetaFields.MetaFieldsBuilder()
                        .setGlobalKey(hashCDM(inputMoney.toBuilder().build())).build())
                .build()
        val newMoneyJson = serializeCdmObjectIntoJson(newMoney)
        println("newMoneyJson ##############################")
        println(newMoneyJson)
        println("newMoneyJson ##############################")
        return newMoney
    }

    fun partyBuilder(partyJson: String): Party {
        val inputParty = parsePartyFromJson(partyJson)
        println("inputParty ##############################")
        println(inputParty)
        println("inputParty ##############################")
        return inputParty
    }
}