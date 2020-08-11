package com.mongodb.channelsproject.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.bson.types.ObjectId

open class FolderRealm (_name: String = "Folder", user_id: String = "5f32cea20bb86daf19246865" ) : RealmObject() {
    @PrimaryKey var _id: ObjectId = ObjectId()
    var _partition: String = user_id
    var name: String = _name

}