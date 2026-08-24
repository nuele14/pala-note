package com.es1.companion.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_rules")
data class TagRuleEntity(
    @PrimaryKey
    val tag: String,
    val systemPrompt: String,
    val targetFolder: String,
    val outputFormat: String = "markdown"
)
