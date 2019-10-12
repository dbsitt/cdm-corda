package net.corda.cdmsupport.validators

import com.rosetta.model.lib.path.RosettaPath
import com.rosetta.model.lib.validation.ValidationResult
import com.rosetta.model.lib.validation.Validator
import net.corda.cdmsupport.AllocatedTotalsNotMatch
import org.isda.cdm.*
import org.isda.cdm.meta.*

class CdmValidators() {

    fun validateEvent(event: Event): List<ValidationResult<in Event>> {
        println("######################## here at the CdmValidators->validateEvent ####################")
        val eventMeta = EventMeta()
        val validators = ArrayList<Validator<in Event>>()
        validators.addAll(eventMeta.choiceRuleValidators())
        validators.addAll(eventMeta.dataRules())
        validators.add(eventMeta.validator())

        return validators.map { it.validate(RosettaPath.valueOf("Event"), event) }.toList()
    }

    fun validateExecution(execution: Execution){
        print("############### here at the CdmValidators->validateExecution #############################")
        //TODO Your code here
        //System.out.println("Execution:" + execution);
    }

    fun validateExecutionPrimitive(executionPrimitive: ExecutionPrimitive) {
        print("############### here at the CdmValidators->validateExecutionPrimitive #############################")
        //TODO Your code here
        //System.out.println("ExecutionPrimitive:" + executionPrimitive);
    }

    fun validateAllocationPrimitive(allocationPrimitive: AllocationPrimitive) {
        println("######################## Validating the AllocationPrimitive ####################")
        print("######################## allocationPrimitive.after.allocatedTrade.size: ${allocationPrimitive.after.allocatedTrade.size} ##########")
        throw AllocatedTotalsNotMatch(allocationPrimitive.before.execution.quantity.amount.toString(),
                allocationPrimitive.after.allocatedTrade[0].execution.quantity.amount.toString())

        //TODO Your code here
        //System.out.println("AllocationPrimitive:" + allocationPrimitive);
    }

    fun validateAffirmation(affirmation: Affirmation) {
        println("######################## here at the CdmValidators->validateAffirmation ####################")
        //TODO Your code here
        //System.out.println("Affirmation:" + affirmation);
    }

}
