package com.example.marcador

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MarcadorCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return MarcadorCarScreen(carContext)
    }
}