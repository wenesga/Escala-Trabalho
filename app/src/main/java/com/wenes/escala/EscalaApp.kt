package com.wenes.escala

import android.app.Application
import com.wenes.escala.automacao.Notificacoes
import com.wenes.escala.data.Repositorio

class EscalaApp : Application() {
    val repositorio by lazy { Repositorio(this) }

    override fun onCreate() {
        super.onCreate()
        Notificacoes.criarCanal(this)
    }
}
