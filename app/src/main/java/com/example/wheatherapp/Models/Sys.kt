package com.example.wheatherapp.Models

import java.io.Serializable

data class Sys(
    val type : Int,
    val id : Long,
    val message : Double,
    val country : String,
    val sunrise : Double,
    val sunset : Double

): Serializable
