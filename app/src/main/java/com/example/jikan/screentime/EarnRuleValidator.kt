package com.example.jikan.screentime

import com.example.jikan.data.EarnRule

object EarnRuleValidator {
    fun validate(rule: EarnRule): List<String> {
        val errors = mutableListOf<String>()
        if (rule.packageName.isBlank()) errors += "packageName is required"
        if (rule.requiredMinutes <= 0) errors += "requiredMinutes must be positive"
        if (rule.rewardMinutes <= 0) errors += "rewardMinutes must be positive"
        if (rule.dailyLimitMinutes <= 0) errors += "dailyLimitMinutes must be positive"
        if (rule.dailyLimitMinutes < rule.rewardMinutes) {
            errors += "dailyLimitMinutes must be at least rewardMinutes"
        }
        return errors
    }
}
