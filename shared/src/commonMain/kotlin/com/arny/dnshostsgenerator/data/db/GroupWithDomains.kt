package com.arny.dnshostsgenerator.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class GroupWithDomains(
    @Embedded val group: GroupEntity,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val domains: List<DomainEntity>,
)
