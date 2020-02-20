package com.padana.ftpsync.folder_list

import java.io.Serializable

class Folder : Serializable {
    var name: String
    var isFolder: Boolean = false
    var path: String
    var isSync: Boolean = false

    constructor(name: String, isFolder: Boolean, path: String) {
        this.name = name
        this.isFolder = isFolder
        this.path = path
    }

}
