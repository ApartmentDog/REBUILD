package com.evyr.rebuild.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun zoneToString(zone: Zone): String = zone.name

    @TypeConverter
    fun stringToZone(value: String): Zone = Zone.valueOf(value)

    @TypeConverter
    fun kindToString(kind: ItemKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): ItemKind = ItemKind.valueOf(value)

    @TypeConverter
    fun sessionTypeToString(type: SessionType): String = type.name

    @TypeConverter
    fun stringToSessionType(value: String): SessionType = SessionType.valueOf(value)

    @TypeConverter
    fun reminderTriggerToString(t: ReminderTrigger): String = t.name

    @TypeConverter
    fun stringToReminderTrigger(value: String): ReminderTrigger = ReminderTrigger.valueOf(value)

    @TypeConverter
    fun placeConditionToString(c: PlaceCondition): String = c.name

    @TypeConverter
    fun stringToPlaceCondition(value: String): PlaceCondition = PlaceCondition.valueOf(value)

    @TypeConverter
    fun attachmentKindToString(kind: AttachmentKind): String = kind.name

    @TypeConverter
    fun stringToAttachmentKind(value: String): AttachmentKind = AttachmentKind.valueOf(value)

    @TypeConverter
    fun recurrenceTypeToString(type: RecurrenceType): String = type.name

    @TypeConverter
    fun stringToRecurrenceType(value: String): RecurrenceType = RecurrenceType.valueOf(value)

    @TypeConverter
    fun tagsToString(tags: List<String>): String = tags.joinToString(",")

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
