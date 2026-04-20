package com.xenonbyte.anr.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.xenonbyte.anr.Falcon;
import com.xenonbyte.anr.FalconHealthState;
import com.xenonbyte.anr.FalconLifecycleState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FalconIntegrationTest {

    @Before
    public void setUp() {
        Falcon.startMonitoring();
        Falcon.resetHealthMonitor();
    }

    @After
    public void tearDown() {
        Falcon.stopMonitoring();
        Falcon.resetHealthMonitor();
        SampleEventStore.get().clear();
    }

    @Test
    public void testFalconIsInitialized() {
        assertEquals(FalconLifecycleState.MONITORING, Falcon.getLifecycleState());
        assertTrue(Falcon.getHealthState() != FalconHealthState.NOT_INITIALIZED);
    }

    @Test
    public void testHealthApisReturnStructuredState() {
        assertNotNull(Falcon.getHealthStatus());
        assertTrue(Falcon.getHealthStatus().length() > 0);
        assertTrue(Falcon.getHealthState() == FalconHealthState.HEALTHY
                || Falcon.getHealthState() == FalconHealthState.DEGRADED);
    }

    @Test
    public void testResetHealthMonitor() {
        Falcon.resetHealthMonitor();
        assertNotNull(Falcon.getHealthStatus());
        assertTrue(Falcon.getHealthState() == FalconHealthState.HEALTHY
                || Falcon.getHealthState() == FalconHealthState.DEGRADED);
    }

    @Test
    public void testStopAndRestartMonitoring() throws InterruptedException {
        Falcon.stopMonitoring();
        Thread.sleep(200L);
        assertEquals(FalconLifecycleState.STOPPED, Falcon.getLifecycleState());

        Falcon.startMonitoring();
        Thread.sleep(200L);
        assertEquals(FalconLifecycleState.MONITORING, Falcon.getLifecycleState());
    }

    @Test
    public void testApplicationContext() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.xenonbyte.anr.sample", appContext.getPackageName());
    }

    @Test
    public void testEventStoreSnapshotIsAvailable() {
        assertNotNull(SampleEventStore.get().snapshot());
        assertNotNull(SampleEventStore.get().snapshot().getEvents());
    }
}
