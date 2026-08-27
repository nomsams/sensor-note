package org.havenapp.main.ui

import org.havenapp.main.model.EventTrigger
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class TimelineViewTest {

    @Test
    fun testEventTriggerSorting() {
        val events = mutableListOf<EventTrigger>()
        
        val t1 = EventTrigger()
        t1.time = Date(1000)
        t1.type = EventTrigger.ACCELEROMETER
        
        val t2 = EventTrigger()
        t2.time = Date(500)
        t2.type = EventTrigger.LIGHT
        
        val t3 = EventTrigger()
        t3.time = Date(1500)
        t3.type = EventTrigger.MICROPHONE
        
        events.add(t1)
        events.add(t2)
        events.add(t3)
        
        val sorted = events.sortedBy { it.time }
        
        assertEquals(500L, sorted[0].time!!.time)
        assertEquals(1000L, sorted[1].time!!.time)
        assertEquals(1500L, sorted[2].time!!.time)
    }
    
    @Test
    fun testEventTriggerEquality() {
        val t1 = EventTrigger()
        t1.id = 1
        t1.type = EventTrigger.ACCELEROMETER
        t1.time = Date(1000)
        t1.eventId = 100
        t1.path = "test"
        
        val t2 = EventTrigger()
        t2.id = 1
        t2.type = EventTrigger.ACCELEROMETER
        t2.time = Date(1000)
        t2.eventId = 100
        t2.path = "test"
        
        assertTrue("Equal EventTriggers should be equal", t1.equals(t2))
        assertEquals(t1.hashCode(), t2.hashCode())
        
        val t3 = EventTrigger()
        t3.id = 2
        assertFalse("Different ID should not be equal", t1.equals(t3))
    }
}
