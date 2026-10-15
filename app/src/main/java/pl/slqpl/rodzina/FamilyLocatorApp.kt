package pl.slqpl.rodzina

import android.app.Application
import androidx.room.Room
import pl.slqpl.rodzina.cloud.FamilyCloud
import pl.slqpl.rodzina.data.LocationDatabase
import pl.slqpl.rodzina.location.TrackingState

class FamilyLocatorApp : Application() {
    val database by lazy {
        Room.databaseBuilder(this, LocationDatabase::class.java, "locations.db").build()
    }
    val cloud by lazy { FamilyCloud() }
    val tracking = TrackingState()
}
