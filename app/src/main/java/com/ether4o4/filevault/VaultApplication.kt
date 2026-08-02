package com.ether4o4.filevault

import android.app.Application
import com.ether4o4.filevault.data.VaultRepository
import com.ether4o4.filevault.data.VaultStorage
import com.ether4o4.filevault.data.db.VaultDatabase

/** Holds process-wide singletons. Kept deliberately tiny (no DI framework). */
class VaultApplication : Application() {

    lateinit var repository: VaultRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = VaultDatabase.get(this)
        repository = VaultRepository(db.fileNodeDao(), VaultStorage(this))
    }
}
