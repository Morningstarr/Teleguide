package com.mongodb.alliance.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import org.bson.types.ObjectId

open class UserRealm (_name : String = "username") : RealmObject(){
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var user_id : String = _id.toString()
    var _partition: String = user_id
    var name: String = _name
    var image: String? = null
}