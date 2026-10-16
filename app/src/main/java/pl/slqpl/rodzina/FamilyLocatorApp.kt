package pl.slqpl.rodzina

import android.app.Application
import androidx.room.Room
import pl.slqpl.rodzina.cloud.FamilyCloud
import pl.slqpl.rodzina.data.LocationDatabase
import pl.slqpl.rodzina.location.TrackingState

class FamilyLocatorApp : Application() {
    private val defaultDatabase by lazy {
        Room.databaseBuilder(this, LocationDatabase::class.java, "locations.db").addMigrations(LocationDatabase.MIGRATION_1_2).build()
    }
    private val defaultCloud by lazy { FamilyCloud() }
    val database get() = testDatabase ?: defaultDatabase
    val cloud get() = testCloud ?: defaultCloud
    // Instrumentation uses an isolated Room database and named Firebase clients connected to emulators.
    @androidx.annotation.VisibleForTesting internal var testDatabase: LocationDatabase? = null
    @androidx.annotation.VisibleForTesting internal var testCloud: FamilyCloud? = null
    val tracking = TrackingState()
}
