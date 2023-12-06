package com.optic.uberclonekotlin.models

import com.beust.klaxon.*

data class Client (
    var id: String? = null,
    val name: String ? = null,
    val lastname: String ? = null,
    val email: String ? = null,
    val phone: String ? = null,
    var image: String ? = "",
    var token: String ? = null,
)
