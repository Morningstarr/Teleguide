package com.mongodb.alliance.model

import io.realm.RealmObject
import io.realm.annotations.Ignore
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.bson.types.ObjectId

open class ChannelRealm (_name: String = "Channel", partition: String = "") : RealmObject() {
    @PrimaryKey var _id: ObjectId = ObjectId()
    var _partition: String = partition
    var name: String = _name
    var displayName: String = "без названия"
    var folder: FolderRealm? = null
    var isPinned: Boolean = false
    var order: Int = 1

    @Required
    private var type: String = ChannelType.channel.displayName
    var typeEnum: ChannelType
        get() {
            return try {
                ChannelType.valueOf(type)
            } catch (e: IllegalArgumentException) {
                ChannelType.channel
            }
        }
        set(value) { type = value.displayName }
}