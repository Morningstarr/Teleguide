package com.mongodb.alliance.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.bson.types.ObjectId

open class FolderRealm (_name: String = "Folder_name", user_id: String = "user_id" ) : RealmObject() {
    @PrimaryKey var _id: ObjectId = ObjectId()
    var _partition: String = user_id
    var name: String = _name

}