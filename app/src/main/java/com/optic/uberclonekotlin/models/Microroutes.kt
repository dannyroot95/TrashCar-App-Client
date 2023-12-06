package com.optic.uberclonekotlin.models

data class Microroutes(
    var turn: String? = null,
    val name: String ? = null,
    val id: String ? = null,
    val descript: String ? = null,
    val coverage: ArrayList<Coverage> = ArrayList(),
    val positions: ArrayList<Positions> = ArrayList()
)

data class Coverage(
    var activity: String? = null,
    var avenue: String? = null,
    var description: String? = null,
    var distance: String? = null
)

data class Positions(
    var lat: Double? = 0.0,
    var lng: Double? = 0.0,
)