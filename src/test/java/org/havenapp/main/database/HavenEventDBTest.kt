package org.havenapp.main.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.havenapp.main.model.Event
import org.havenapp.main.model.EventTrigger
import org.junit.Assert.*
import org.junit.Test

class HavenEventDBTest {

    @Test
    fun testDatabaseCreation() {
        val context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, HavenEventDB::class.java).build()
        
        assertNotNull(\"Database should be created\", db)
        db.close()
    }

    @Test
    fun testEventInsertAndRetrieve() {
        val context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, HavenEventDB::class.java).build()
        
        val event = Event()
        event.startTime = java.util.Date()
        event.endTime = java.util.Date()
        event.title = \"Test Event\"
        
        db.getEventDAO().insertEvent(event)
        
        val events = db.getEventDAO().getAllEvent()
        assertEquals(1, events.size)
        assertEquals(\"Test Event\", events[0].title)
        
        db.close()
    }

    @Test
    fun testEventTriggerInsertAndRetrieve() {
        val context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, HavenEventDB::class.java).build()
        
        val trigger = EventTrigger()
        trigger.type = EventTrigger.ACCELEROMETER
        trigger.time = java.util.Date()
        trigger.path = \"test_path\"
        trigger.eventId = 1
        
        db.getEventTriggerDAO().insertEventTrigger(trigger)
        
        val triggers = db.getEventTriggerDAO().getAllEventTriggers()
        assertEquals(1, triggers.size)
        assertEquals(EventTrigger.ACCELEROMETER, triggers[0].type)
        assertEquals(\"test_path\", triggers[0].path)
        
        db.close()
    }
}
